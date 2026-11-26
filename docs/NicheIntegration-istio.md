# Niche integration: Istio service mesh (owner: Mouheb)

> Status: **week 1, preparation, done 2026-11-26.** The cluster runs Istio's Bookinfo sample, not DoseRunner yet.
> The DoseRunner services move into the mesh in week 3.

## 1. Why Istio (the problem it solves)
DoseRunner has 4 Spring services plus a robot simulator. Without a mesh, each service handles the network itself:
TLS between services, retries, timeouts, rate limits, and trace propagation.
Istio moves that work into an Envoy **sidecar** next to every pod, configured by `istiod`:

| Need in DoseRunner | Without a mesh (today, compose) | With Istio (week 3) |
|---|---|---|
| Encrypt service-to-service traffic | plain HTTP inside the compose network | automatic **mTLS** (`PeerAuthentication` STRICT), certificates rotated by istiod |
| "Only order-service may call dispatch gRPC" | nothing stops another container | `AuthorizationPolicy` on the caller's SPIFFE identity |
| Reject a bad JWT before it reaches the app | each service validates it (`commons/sec`) | `RequestAuthentication` at the gateway with identity's JWKS. The app check stays as defence in depth |
| One entry point, routes by prefix | 4 ports (8081–8084) | `Gateway` + `VirtualService`: `/auth`, `/admin` → identity · `/orders`, `/pharmacy`, `/formulary` → order · `/fleet` → dispatch · `/custody` → custody |
| Rate limit | `RateLimitFilter` in each JVM (Bucket4j, per user) | plus Envoy **local rate limit** on the gateway (per pod, before the JVM) |
| See who calls whom | logs + traceId | Kiali graph, Jaeger traces, Prometheus/Grafana metrics, with no code change |
| Retries / timeouts / circuit breaking | hand-written per client | `DestinationRule` (outlier detection) + `VirtualService` (retries, timeout) |

The application code doesn't change. The `traceparent` header already handled by `CorrelationIdFilter` is the
same W3C header Envoy propagates, so Jaeger spans and the `traceId` in our JSON logs will match.

## 2. What was built in week 1
| File | Purpose |
|---|---|
| `deploy/istio/kind-config.yaml` | kind cluster `doserunner` (1 control-plane node) |
| `deploy/istio/bookinfo-setup.sh` | cluster → `istioctl install --set profile=demo` → sidecar injection → Bookinfo → addons → checks |
| `deploy/istio/peer-authentication-strict.yaml` | mesh-wide strict mTLS (reused for DoseRunner in week 3) |

Versions: kind v0.33.0 (node image `kindest/node:v1.37.0`) · Istio 1.32.0 · kubectl 1.36 (bundled with Docker Desktop).
The first run of the script took about 5 minutes (node image + Istio + addon images pulled once).

## 3. How to try it
```bash
# tools: kind, istioctl on PATH; ISTIO_HOME = unzipped istio-1.32.0 (holds samples/)
ISTIO_HOME=~/tools/istio-1.32.0 sh deploy/istio/bookinfo-setup.sh

kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80
# http://localhost:8080/productpage
for i in $(seq 1 100); do curl -s -o /dev/null http://localhost:8080/productpage; done
istioctl dashboard kiali

kind delete cluster --name doserunner    # free the RAM afterwards
```
On kind the ingress gateway's `LoadBalancer` service stays `<pending>` (no cloud load balancer), so we use
`port-forward`.

## 4. What to observe (and what it proves)
1. **Sidecar injection:** `kubectl get pods` shows `2/2` per pod. The namespace label `istio-injection=enabled`
   makes istiod's mutating webhook add an `istio-proxy` container. A pod created before the label has `1/1` and
   is outside the mesh until restarted.
2. **Kiali graph:** productpage → details, productpage → reviews (v1/v2/v3) → ratings. The 3 `reviews` versions
   share the traffic round-robin. That's the basis for the week 3 canary of `dispatch-strategy-v2`.
3. **mTLS:** after `peer-authentication-strict.yaml`, Kiali shows a lock on every edge. A pod without a sidecar
   gets `connection reset` when it calls `productpage:9080` in plain text. That's the proof that STRICT is enforced.
   **Checked 2026-11-26:** through the gateway `/productpage` still answers 200 (sidecar to sidecar = mTLS), while a
   `curlimages/curl` pod with `sidecar.istio.io/inject=false` calling `http://productpage.default:9080/productpage`
   fails with curl exit 56 (connection reset by the receiving Envoy).
4. **Tracing:** Jaeger shows one trace for productpage → reviews → ratings. Envoy creates the spans. The app only
   forwards the headers.

## 5. Resource use (decides whose machine hosts the week 3 cluster)
| What | Measured |
|---|---|
| Host | 24 GB RAM, 16 threads, Docker VM limit 12 GB |
| kind node after Istio demo + Bookinfo + addons, idle (`docker stats`) | **2.4 GB**, CPU about 17 % |
| Same, after 150 requests + strict mTLS | **2.6 GB**, CPU about 18 % |
| Pods | 6 Bookinfo (2/2 each), istiod, 2 gateways, Kiali, Jaeger, Prometheus, Grafana, Loki |
| Plus 5 DoseRunner JVMs (about 300-400 MB each with sidecar) + Postgres + Artemis (week 3 estimate) | about 5-6 GB in total |

Measured, the mesh itself is far below the blueprint estimate of about 16 GB, so this machine can host the week 3
cluster. If memory gets tight, options are the `minimal`
profile (no egress gateway, lower trace sampling), dropping Grafana, and one replica per service.

## 6. Week 3 plan (DoseRunner in the mesh)
1. Images: `deploy/compose/Dockerfile` per module → `kind load docker-image doserunner/<svc>:dev`.
2. `deploy/k8s/`: Deployment + Service per service, Postgres + Artemis as plain pods (not meshed, or with
   `PeerAuthentication` PERMISSIVE on their ports).
3. `Gateway` + `VirtualService` with the prefix routes from the README REST table.
4. `RequestAuthentication` (issuer `doserunner-identity`, `jwksUri` = identity's `/.well-known/jwks.json`)
   + `AuthorizationPolicy` requiring a JWT on everything except `/auth/login`, `/.well-known/**` and the UI pages.
5. `AuthorizationPolicy` for gRPC: only the `order-service` service account may call `FleetService`.
6. Envoy local rate limit on the gateway (`EnvoyFilter`), compared with `RateLimitFilter`.
7. Canary: `dispatch-service` v1/v2 with a 90/10 `VirtualService` split, watched in Kiali.

## 7. Decisions and pitfalls
- **kind, not minikube:** it runs in the Docker Desktop we already use, starts in about 1 minute, and loads local
  images with `kind load` (no registry).
- **Sidecar mode, not ambient:** the brief and most docs/examples use sidecars, and Kiali shows them clearly.
  Ambient (ztunnel) uses less RAM. It's the fallback if memory is tight.
- **JWT is checked twice on purpose:** at the gateway (Istio) and in the app (`commons/sec`). The app check keeps
  the compose stack and the tests secure without a mesh.
- Postgres/Artemis speak their own TCP protocols. Envoy passes them through, but strict mTLS breaks clients without
  a sidecar, so they get a PERMISSIVE exception.
- `istioctl analyze` before every demo: it catches a `VirtualService` pointing at a missing host or subset.
