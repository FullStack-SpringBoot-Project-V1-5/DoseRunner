# Contributing to DoseRunner

## Branches
`main` is protected: PR mandatory, **1 approval from someone other than the author**, CI green. No direct push.

| Prefix      | Owner            |
|-------------|------------------|
| `istio/…`   | A                |
| `avro/…`    | B                |
| `artemis/…` | C                |
| `ffm/…`     | D                |
| `ros/…`     | R                |
| `joint/…`   | shared work      |

Anything touching `contracts/` or `commons/` needs review by **all 4**.
Rotating reviewer: A reviews B, B reviews C, C reviews D, D reviews A.

## Commits — Conventional Commits
```
feat(order): reserve stock in OrderFacade.place()
fix(dispatch): keep JMSXGroupID on redelivery
test(custody): flipped byte fails verify
docs(avro): schema evolution section
chore(ci): cache maven
```
Scopes: `identity`, `order`, `dispatch`, `custody`, `robot-sim`, `ros`, `contracts`, `commons`, `native`, `deploy`, `ci`, `docs`.

## Code rules
- Versions live **only** in the parent `pom.xml`.
- Pyramid in every service: `pres → facade → bo → dao`. `pres` never imports `dao`, `bo` never imports
  `org.springframework.web`, `@Transactional` only in `facade` (enforced by an ArchUnit test, week 1).
- Constructor injection only.
- Never commit keys or secrets (`*.pem`, `*.key`, `.env` are gitignored).

## Definition of Done
code + unit test + integration test where there is I/O + logs with traceId + updated `NicheIntegration.md` + PR merged.
