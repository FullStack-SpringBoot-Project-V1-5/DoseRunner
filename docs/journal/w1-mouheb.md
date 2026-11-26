# Week 1 journal: Mouheb (identity-service, deploy, CI · niche: Istio)

## Done
| Day | What | Where |
|---|---|---|
| 1–2 | Day 1 skeleton (parent POM, modules, `commons`, compose, CI) and the v0.1 contracts session with the team. Tag `v0.1-contracts` | `pom.xml`, `commons/`, `contracts/`, `deploy/compose/` |
| 3 | identity-service: `AppUser` / `Role`, Flyway schema + one seeded user per role, `CredentialPolicy`, Pyramid ArchUnit test (the template the others copied) | `identity-service/` |
| 4 | RS256 JWT issuance (Nimbus, `kid` = key thumbprint), `/.well-known/jwks.json`, key from a mounted file (`gen-jwt-key.sh`, never committed) | `identity-service/config`, `deploy/compose/` |
| 4 | `commons/sec`: resource-server auto-config that every service gets by adding one starter (JWKS + issuer check, `roles` → `ROLE_*`, stateless chain, 401/403 as `ApiError` with traceId, method security) | `commons/.../sec` |
| 4 | `test-support`: `TestJwt` signs identity-shaped tokens, so services test 401/403/2xx without identity running | `test-support/` |
| 5 | `RateLimitFilter` (Bucket4j token bucket per JWT `sub` or IP, bounded Caffeine cache, 429 + `Retry-After`) | `commons/.../sec` |
| 5 | Thymeleaf `/login` + `/ui/admin/users` on a second chain (session + form login + CSRF). A browser session never authorizes the REST API | `identity-service/pres/ui` |
| 5 | Istio prep: kind + Istio 1.32 demo + Bookinfo + addons. Strict mTLS checked (a pod without a sidecar is reset). The cluster uses 2.6 GB | `deploy/istio/`, `docs/NicheIntegration-istio.md` |
| 6 | **I2**: security starter + role tests in order, dispatch, custody. Checked live in compose with real tokens (NURSE 201 / PHARMACIST 403 / no token 401) | PR #13 |
| 6–7 | Week 1 gate script and run: login → order → UI validation → Avro on Artemis → dispatch → robot-sim moves, one traceId in 4 services' logs | `deploy/compose/week1-gate.sh` |
| 7 | `develop` integration branch + CI on it, merge-conflict fix on `contracts/pom.xml` (gRPC dependencies lost in the #5 merge), custody ArchUnit test | PRs #8, #9 |

Tests on my side: identity 46 (unit + Testcontainers Postgres + ArchUnit), commons/test-support 10.

## What I learned
- Spring Boot 4 renamed a lot (starters `-webmvc`, `-security-oauth2-resource-server`, `-webmvc-test`; Flyway needs its
  own starter; moved auto-config packages). Checking the Boot BOM first saved time every time.
- Two `SecurityFilterChain`s with `securityMatcher` + `@Order` are the clean way to have a stateless JWT API and a session UI
  in one service.
- A JWT issuer that is not a URL (`doserunner-identity`) works for validation, but `Jwt.getIssuer()` throws. Read it as a
  string claim.
- Istio's sidecar mesh is much lighter than the blueprint feared (2.6 GB vs ~16 GB estimated), so one machine can host
  week 3.

## Problems and how they were solved
- **Merge conflict resolved the wrong way** (`contracts/pom.xml` after #5 kept one side and dropped the gRPC dependencies),
  so `develop` did not compile. I caught it by building the merged branch locally before anyone pulled it, and fixed it in
  PR #9. Lesson: build the merged result, not only each branch.
- PRs were merged into the stacked joint branches instead of the integration branch, so we added `develop` and wrote the
  flow in `CONTRIBUTING.md`.
- custody tests need Linux libsodium. On Windows they run in an `eclipse-temurin:25-jdk` container, the same as CI.

## Open / next week
- Login for the order / dispatch / custody Thymeleaf pages (today `/ui/**` is public there). Team decision needed.
- CI extras: surefire reports on failure, Docker image build job.
- Week 3 prep: Dockerfiles → `kind load`, k8s manifests, Gateway + VirtualService routes, `RequestAuthentication` with
  identity's JWKS.
