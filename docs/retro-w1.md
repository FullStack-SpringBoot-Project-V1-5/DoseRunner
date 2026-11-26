# Week 1 retro (DRAFT, to complete together)

> Draft written from the repo history and the gate run. Each person: correct your line, and add what went well or badly
> for you.

## Gate result
`sh deploy/compose/week1-gate.sh` on 2026-11-26: **PASSED**. Login → JWT → order 201 → pharmacist validates in the UI →
`OrderValidated` (Avro) on Artemis → dispatch assigns R-02 → robot-sim moves (6 positions), one traceId in the logs of
identity, order, dispatch and robot-sim.

| Gate criterion | Status |
|---|---|
| Flow in docker-compose with one traceId | ✅ |
| Each service: unit tests + ≥ 1 Testcontainers test + ArchUnit | ✅ identity, order, dispatch, custody (custody ArchUnit added day 7). robot-sim has no Pyramid layers, so its ArchUnit is n/a |
| FFM Docker image signs and verifies | ✅ custody runs on libsodium in compose, sign/verify tests pass in Linux |
| I1 contracts frozen (`v0.1-contracts`) | ✅ |
| I2 JWT accepted by all services | ✅ (PR #13, checked live) |
| I3 order → dispatch with the same traceId, robot moves | ✅ |

## What slipped
- **custody does not consume Artemis events yet**: the ledger is filled only by tests / the `demo` profile. A new order has
  no custody record (`/verify` → 404). Owner: Chouket, week 2 day 1.
- **ros-bridge**: no code pushed yet (turtlesim + rosjava hello). Owner: Knani.
- **dispatch REST API** (`/fleet/robots`, `/fleet/missions`, `/fleet/dlq`, `/fleet/strategy`) from the README is not built.
  Only the fleet board page exists. Owner: Gueddes.
- `POST /orders/{id}/delivery` and `/pharmacy/orders/{id}/pickup` are not built. Owner: Chbel.

## What we cut / decided
- **Thymeleaf pages of order, dispatch and custody are public** (`/ui/**`, demo actor ids). Only identity has a form login.
  To decide: a shared login (identity issues the JWT in a cookie), or a per-service form login. _Decision: ..._
- Integration branch **`develop`** added: features → `develop`, checkpoints → `main`.
- **Avro / gRPC: keep Avro** (no fallback to Protobuf). `AvroMessageCodec` (Artemis) and `AvroMarshaller` (gRPC) both have
  round-trip tests, and the Avro events work end to end in the gate. _Confirm: Chbel._

## Process issues
- Merge conflicts were resolved by picking one side. This broke `develop` (`contracts/pom.xml`). Rule: keep both sides,
  then build the merged result before merging the PR.
- Some PRs were merged before their CI run finished. To check together: did the `contracts/` / `commons/` PRs get all
  4 approvals? To fix: branch protection on `develop` (1 approval + CI green), and a team call for `contracts/` /
  `commons/` PRs.

## Week 2 first steps
1. Chouket: custody Artemis listener → `record()` (then the gate also shows the custody chain).
2. Team: UI login decision.
3. Gueddes / Chbel: missing REST endpoints from the README table.
4. Knani: ros-bridge minimum (turtlesim + RobotLink).
