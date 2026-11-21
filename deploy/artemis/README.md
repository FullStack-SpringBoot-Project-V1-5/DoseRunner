# Artemis broker config (owner: C)

## Addresses — LOCKED at `v0.1-contracts`
Java constants: `contracts/.../messaging/Addresses.java` and `MessageHeaders.java`. `broker.xml` must match them.

| Address              | Routing   | Queues                                                    | Payload                 |
|----------------------|-----------|-----------------------------------------------------------|-------------------------|
| `doserunner.events`  | multicast | `order-service`, `dispatch-service`, `custody-service`    | all `events/*.avsc`     |
| `robot.commands`     | anycast   | `robot.commands` (grouped by `JMSXGroupID=robotId`)       | `MissionCommand`        |
| `robot.telemetry`    | multicast | `dispatch-service.telemetry`                              | `RobotTelemetry`        |
| `DLQ`                | anycast   | `DLQ`                                                     | after 3 failed deliveries |
| `ExpiryQueue`        | anycast   | `ExpiryQueue`                                             | expired messages        |

Consumers subscribe to multicast queues with the FQQN `address::queue`, e.g. `doserunner.events::dispatch-service`.

## Message properties (every message)
`BytesMessage` with an Avro single-object body, plus `eventType` (record simple name), `traceId`, `orderId`;
`robotId` + `JMSXGroupID` on `robot.commands`; `_AMQ_SCHED_DELIVERY` for `DispatchTimedOut`.

## C004 — broker configuration

On 2026-11-21 Wassim approved a correction to the frozen Day 2 queue-binding
specification: keep `doserunner.events::dispatch-service`, but use
`robot.telemetry::dispatch-service.telemetry`. Artemis queue names are globally
unique even when consumers use FQQNs. `Addresses.QUEUE_DISPATCH_TELEMETRY` records
the corrected queue name. No Avro schema/generated-class change is involved.

`broker.xml` preserves the 2.44.0 image-generated broker name, persistent ASYNCIO
journal/directories, protocol acceptors and `amq` security role permissions.
The five addresses and seven queues above are explicitly declared. Authentication
still uses the instance's existing users/roles; anonymous access remains disabled
by Compose. Ports remain 61616 and 8161. Queue durability is verified at runtime.
The inherited stock management-address and auto-create settings are retained.

The application-address catch-all sets:

- `max-delivery-attempts=3`, `dead-letter-address=DLQ`, `expiry-address=ExpiryQueue`.
- **Local timing choices**, not Notion requirements: initial delay **1000 ms**,
  multiplier **2.0**, maximum delay **10000 ms**. Thus the two retries wait about
  1 and 2 seconds; the third failed delivery goes to DLQ.
- DLQ and ExpiryQueue use unlimited delivery attempts so inspecting/retrying those
  queues does not cycle their messages through the application retry limit.

JMSXGroupID provides ordering, not target routing; the frozen command convention
also requires the `robotId` selector. No listeners or business message publishers
are implemented in C004. HA remains deferred.

### Compose and existing instances

The actual `apache/activemq-artemis:2.44.0` `/docker-run.sh` copies `etc-override`
only during first-time instance creation. Mounting an override alone therefore
leaves an existing instance on its old configuration. `start-broker.sh` copies
only broker.xml before launching the original entrypoint when the instance
already exists. For a fresh instance, the original entrypoint creates credentials
and copies the override itself. Both paths preserve the image's initialization.
Compose mounts the XML and wrapper read-only; the instance's existing broker.xml
is updated on every start. Future XML edits require an Artemis restart.

Back up the existing XML before rolling out. From the repository root:

```sh
rtk proxy docker cp doserunner-artemis-1:/var/lib/artemis-instance/etc/broker.xml /tmp/broker-before-c004.xml
rtk proxy docker inspect doserunner-artemis-1 --format '{{json .Mounts}}'
rtk proxy docker compose -f deploy/compose/docker-compose.yml config -q
rtk proxy docker compose -f deploy/compose/docker-compose.yml up -d --no-deps artemis
```

Compose recreates only the Artemis container when its mounts/entrypoint change;
it reuses the anonymous instance volume. Verify that the volume identity is the
same afterward. Do not use `down -v`, `--renew-anon-volumes`, delete the volume,
or reinitialize the broker. PostgreSQL and other services are not rollout targets.
The image's creation-time credentials cannot change an existing user's password.

### Verification

With the NixOS Java 25/Maven environment in docs/VERIFICATION.md and Docker access:

```sh
export JAVA_HOME=/run/current-system/sw/lib/openjdk
export MAVEN_USER_HOME=/tmp/doserunner-maven
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service -am -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=ArtemisBrokerIT verify
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository verify
```

The focused command selects only the two Failsafe broker tests; `NoSuchTest`
intentionally excludes Surefire tests from that run. Full verify includes them.
The test uses Testcontainers GenericContainer with the exact official broker
image, project XML and startup wrapper. The BOM-managed Jakarta Artemis client
is test-only (currently 2.53.0); no application JMS wiring is added.

Broker startup performs XSD validation. Tests compare the loaded XML bytes with
the project file, inspect all queue/address routing and durable flags through the
management API, verify application retry policies and reject a wrong password.
A synthetic BytesMessage is successfully committed; a poison message is delivered
with JMSXDeliveryCount 1, 2, 3 and rolled back each time. Tests check retry delays,
no early DLQ message, original queue/address metadata, one DLQ copy and no fourth
delivery. These are broker probes, not OrderValidated/MissionCommand payloads or
an application messaging integration proof.

Sources: [address model](https://artemis.apache.org/components/artemis/documentation/latest/address-model.html),
[redelivery](https://artemis.apache.org/components/artemis/documentation/latest/undelivered-messages).
The exact image entrypoint was inspected locally; its override-copy behavior
must be rechecked before changing the broker image version.

## C005 dispatch event filter (repository configuration only)

The `dispatch-service` queue now has `eventType = 'OrderValidated'`; the listener
retains the same selector. Other queues, telemetry correction and C004 settings
are unchanged. Disposable Artemis tests verify the actual queue filter, preserve
other services' event routing and exercise listener retry/DLQ behavior.

No running persistent queue was changed. Existing-instance rollout requires a
separately authorized in-place filter update plus matching XML, with queue identity
and retained messages verified. A restart/XML edit alone is not claimed to update
an existing queue's filter. Do not delete, recreate or purge queues. The filter does
not remove old unmatched messages. See dispatch-service/README.md for the rollout,
manual single-message DLQ replay, outbox recovery and finite duplicate-cache limits.
