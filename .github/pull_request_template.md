<!-- Title: Conventional Commits, e.g. "feat(order): reserve stock on place()" -->

## What

## Why

## How tested
- [ ] unit tests
- [ ] integration test (Testcontainers) where there is I/O
- [ ] manually in docker-compose (describe)

## Checklist
- [ ] `NicheIntegration-*.md` updated (or N/A)
- [ ] logs carry the traceId
- [ ] no dependency version added in a child `pom.xml` (versions live in the parent)
- [ ] touches `contracts/` or `commons/`? → **approval from all 4** required
