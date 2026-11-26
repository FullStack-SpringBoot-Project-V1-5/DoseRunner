#!/usr/bin/env sh
# Week 1 Istio try-out: kind cluster + Istio (demo profile) + Bookinfo + addons (Kiali, Jaeger, Grafana, Prometheus).
#
#   ISTIO_HOME=~/tools/istio-1.32.0 sh deploy/istio/bookinfo-setup.sh
#
# Needs on PATH: docker, kind, kubectl, istioctl. ISTIO_HOME is the unzipped Istio release (it holds samples/).
# Tear down with:  kind delete cluster --name doserunner
set -eu

: "${ISTIO_HOME:?set ISTIO_HOME to the unzipped Istio release, e.g. ~/tools/istio-1.32.0}"
HERE=$(cd "$(dirname "$0")" && pwd)
CLUSTER=doserunner

step() { printf '\n==> %s\n' "$*"; }

step "kind cluster '$CLUSTER'"
if kind get clusters | grep -qx "$CLUSTER"; then
  echo "already exists, reusing it"
else
  kind create cluster --config "$HERE/kind-config.yaml"
fi
kubectl config use-context "kind-$CLUSTER"

step "Istio (demo profile: istiod + ingress/egress gateways, high trace sampling)"
istioctl install --set profile=demo -y
kubectl label namespace default istio-injection=enabled --overwrite

step "Bookinfo (4 services, 3 versions of reviews), each pod gets an Envoy sidecar"
kubectl apply -f "$ISTIO_HOME/samples/bookinfo/platform/kube/bookinfo.yaml"
kubectl apply -f "$ISTIO_HOME/samples/bookinfo/networking/bookinfo-gateway.yaml"
kubectl wait --for=condition=available deployment --all -n default --timeout=300s

step "Addons: Prometheus, Grafana, Jaeger, Kiali"
kubectl apply -f "$ISTIO_HOME/samples/addons"
kubectl rollout status deployment/kiali -n istio-system --timeout=300s

step "Checks"
istioctl analyze -n default
kubectl get pods -n default   # every pod must show READY 2/2 (app + istio-proxy)

cat <<'EOF'

Done. Next:
  kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80   # then http://localhost:8080/productpage
  istioctl dashboard kiali                                                # graph: generate traffic first (loop below)
  istioctl dashboard jaeger
  istioctl dashboard grafana
  for i in $(seq 1 100); do curl -s -o /dev/null http://localhost:8080/productpage; done

  kubectl apply -f deploy/istio/peer-authentication-strict.yaml           # strict mTLS, see the lock in Kiali
  docker stats --no-stream doserunner-control-plane                       # RAM used by the whole cluster
EOF
