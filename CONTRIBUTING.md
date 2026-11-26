# Contributing to DoseRunner

## Branches
| Branch    | Role                                                                                   |
|-----------|----------------------------------------------------------------------------------------|
| `develop` | integration branch: **every feature PR targets `develop`**                             |
| `main`    | demo-ready only: updated by a `develop` → `main` PR at each checkpoint (end of week, demo) |

Both are protected: PR mandatory, **1 approval from someone other than the author**, CI green. No direct push.

- Start new work from `develop`: `git fetch origin && git switch -c avro/my-feature origin/develop`.
- Keep your branch current with `git merge origin/develop` (not rebase once it is pushed).
- Merge PRs with a **merge commit**, not squash (PRs are often stacked on each other).
- Tags: contract freezes (`v0.1-contracts`) on `develop`; release tags (`v1.0`) on `main`.

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
