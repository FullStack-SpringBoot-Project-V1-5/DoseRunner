# Niche integration — Apache Avro (owner: Chbel)

Status: **week 1 (days 3–7)**. Avro is the single contract language of DoseRunner, for async messages on
Artemis and for sync gRPC calls between order-service and dispatch-service.

## 1. Why Avro (the problem it solves)

DoseRunner has two kinds of consumers that cannot be redeployed together:

- **Services** (order, dispatch, custody) released by four different people.
- **Robots** whose firmware is updated one by one. A v1 and a v2 robot run on the same floor at the same time.

Java serialization breaks as soon as a class changes, so it can't handle this. JSON has no enforced schema,
and it lets a producer silently drop a field that a consumer still needs. Avro gives us:

| Need | Avro feature |
|---|---|
| One contract shared by 4 services and the robots | `.avsc` schemas in `contracts/`, Java classes generated at build time |
| A consumer can read a newer or older producer | Schema resolution (writer schema vs reader schema, defaults for added fields) |
| Know *which* version wrote a message, without a registry server | **Single-object encoding**: 2-byte marker + CRC-64-AVRO fingerprint of the writer schema |
| The same language for sync calls | gRPC is format-agnostic: `MethodDescriptor.Marshaller<T>` backed by Avro |
| Compact messages for robots | Binary encoding, no field names on the wire |

If you remove Avro, robot firmware v1/v2 can no longer coexist and every schema change becomes a
coordinated big-bang deployment.

## 2. What was built

### 2.1 `contracts` module (shared by everybody)

| Class | Role |
|---|---|
| `ContractCatalog` | The list of every generated contract class (events, robot, gRPC). |
| `messaging.AvroMessageCodec` | Single-object encode/decode. `encode(record) → byte[]`, `decode(bytes, Type.class)`, `decode(bytes)` (type found from the fingerprint), `registerWriterSchema(schema)` for v1/v2 coexistence. Calls `AvroTrust.install()`. |
| `messaging.AvroJmsMessages` | JMS adapter: `toMessage(session, record)` builds a `BytesMessage` and sets `eventType`. `fromMessage(message[, Type.class])` reads it back. |
| `grpc.AvroMarshaller<T>` | `io.grpc.MethodDescriptor.Marshaller<T>` on top of the codec. Decode failures become `Status.INTERNAL`. |
| `grpc.FleetServiceDescriptors` | `GET_ETA` (unary, safe), `RESERVE_ROBOT` (unary, idempotent) and the `ServiceDescriptor` for `doserunner.FleetService`. Dispatch binds them on the server, order uses them on the client. No `.proto`, no protoc. |
| `AvroCodecException` | Every codec failure (bad marker, unknown fingerprint, truncated payload, wrong type). |

`jakarta.jms-api` is an *optional* dependency of `contracts`: services that publish already get it from
`spring-boot-starter-artemis`, and the codec itself does not need it.

### 2.2 How a service uses it

Publishing (what order-service does):

```java
jmsTemplate.send(Addresses.EVENTS, session -> {
    BytesMessage message = avroJmsMessages.toMessage(session, orderValidated);
    message.setStringProperty(MessageHeaders.ORDER_ID, orderId);
    message.setStringProperty(MessageHeaders.TRACE_ID, MDC.get("traceId"));
    return message;
});
```

Consuming (what dispatch-service will do for I3):

```java
@JmsListener(destination = "doserunner.events::dispatch-service", selector = "eventType = 'OrderValidated'")
void on(Message message) throws JMSException {
    OrderValidated event = avroJmsMessages.fromMessage(message, OrderValidated.class);
}
```

gRPC (week 2, I4):

```java
EtaReply reply = ClientCalls.blockingUnaryCall(channel, FleetServiceDescriptors.GET_ETA, callOptions, request);
ServerServiceDefinition.builder(FleetServiceDescriptors.SERVICE)
        .addMethod(FleetServiceDescriptors.GET_ETA, ServerCalls.asyncUnaryCall(this::getEta));
```

### 2.3 order-service (the business side)

| Layer | Content |
|---|---|
| `pres` | REST `/orders`, `/pharmacy/*`, `/formulary/*`, Thymeleaf `/ui/orders/new`, `/ui/orders/{id}`, `/ui/pharmacy/queue`, `CurrentActor` |
| `facade` | `OrderFacade.place()` reserves stock and saves the order in **one transaction**. Also `validate()`, `reject()` (releases stock), and `FormularyFacade` (`@Cacheable("formulary")`, evicted on update) |
| `bo` | `OrderBO` (state machine, controlled-substance cap of 10 units), `StockBO` (reserve/release), `FormularyBO` |
| `dao` | JPA entities + repositories; the stock row is read with `PESSIMISTIC_WRITE` so concurrent orders never oversell |
| `messaging` | `OrderEventPublisher`: maps to `OrderPlaced` / `OrderValidated` and sends **after commit** |
| `shared` | `OrderStatus` + domain exceptions (transversal, used by every layer, depends on none) |

Flyway: `V1__init.sql` (drug, stock_item, ward, medication_order) and `V2__seed_formulary.sql` (22 drugs, 7 of them
controlled; 8 wards with their grid cell, and `PHARMACY` is not a delivery destination).

## 3. Wire format

```
+------+------+----------------------------+-------------------------------+
| 0xC3 | 0x01 | CRC-64-AVRO fingerprint     | Avro binary body              |
|      |      | (8 bytes, little endian)   | (writer schema)               |
+------+------+----------------------------+-------------------------------+
```

- JMS: always a `BytesMessage`, with the properties `eventType`, `orderId`, `traceId` (+ `robotId`/`JMSXGroupID` on
  `robot.commands`).
- gRPC: the same bytes are the HTTP/2 message payload. A server can tell exactly which schema version a client used.
- `AvroMessageCodec.encode` produces **byte-for-byte** the same output as the generated `toByteBuffer()`
  (test `encodedBytesAreIdenticalToTheGeneratedClassEncoding`).

## 4. Schema evolution (v1 → v2)

Rules agreed for every contract (ADR 0001):

1. Only add fields **with a default** (`["null", T]` + `default: null`, or an enum with a `default` symbol).
2. Never rename or remove a field without a default; never change a type.
3. A reader that sees a writer it doesn't know fails loudly (`AvroCodecException`) and never misreads.

How the codec handles two versions at once: the decoder resolves the writer schema from the fingerprint through a
`SchemaStore`. A consumer compiled against v1 reads v2 bytes after `registerWriterSchema(v2)`. The test
`readerOnCurrentSchemaReadsBytesFromANewerRegisteredWriterSchema` writes a RobotTelemetry that has an extra
`temperatureC` field and reads it back as the current `RobotTelemetry` class.

Week 2 (not in this PR): `RobotTelemetry` v2 (`status` enum + `temperatureC`), plus the CI compatibility job that
turns a breaking schema PR red.

## 5. Delivery guarantees and their limits

- An event is published **only after the database commit** (`TransactionSynchronization.afterCommit`). An order
  that was rolled back is never announced (`neverSendsWhenTheTransactionRollsBack`, `aRejectedPlacementPublishesNothing`).
- Messages are persistent (`deliveryPersistent=true`) and go to the multicast address `doserunner.events`.
- **Known gap:** if the service crashes between the commit and the send, the event is lost and the failure is only
  logged (ERROR with the orderId). The fix is a transactional outbox. It is an open point for week 2.
- Every event carries a fresh `eventId` (UUID). Consumers deduplicate on it, because delivery is at-least-once.

## 6. Tests

| Level | Test | What it proves |
|---|---|---|
| Unit | `AvroMessageCodecTest` (11) | round-trip of every message, fingerprint header, wrong type / unknown fingerprint / bad marker / truncated payload rejected, v2 writer read by a v1 reader |
| Unit | `AvroJmsMessagesTest` (3) | `BytesMessage` body + `eventType`, non-bytes messages rejected |
| Unit | `AvroMarshallerTest` (5) | round-trip of the 4 gRPC records, defaults/nulls, garbage → `INTERNAL` |
| Integration | `FleetServiceDescriptorsTest` (3) | **real in-process gRPC server and channel** carrying Avro bodies for GetEta and ReserveRobot |
| Unit | `StockBOTest`, `OrderBOTest`, `FormularyBOTest`, `OrderFacadeTest` (mocked repositories), `OrderEventPublisherTest`, `CurrentActorTest` | business rules, after-commit publishing, actor resolution |
| Testcontainers | `OrderPlacementIT` (Postgres + Artemis) | stock and order committed together; 409 with no change when stock is short; **rollback of the reservation** when the insert fails; 8 concurrent orders on 5 units → exactly 5 placed |
| Testcontainers | `FormularyCacheIT` | the second call issues **zero SQL statements** (Hibernate statistics); an update evicts both the item and the list |
| Testcontainers | `OrderEventPublishingIT` | a real topic subscriber on Artemis receives `OrderPlaced` then `OrderValidated`, decodes them, and finds the request's `traceId` in the headers |
| Testcontainers | `UiPagesIT` | Thymeleaf pages render; nurse → pharmacist flow through the forms |
| Architecture | `PyramidArchitectureTest` (ArchUnit) | pres → facade → bo → dao, only facade uses `messaging`, bo has no Web/JPA/JMS, `@Transactional` only in facade |

Run them with `./mvnw -pl order-service -am verify` (Docker must be running).

## 7. How to try it

```bash
docker compose -f deploy/compose/docker-compose.yml up --build -d postgres artemis order-service
curl -s -X POST localhost:8082/orders -H 'Content-Type: application/json' -H 'X-User-Id: u-nurse-1' \
     -d '{"patientRef":"P-0042","drugCode":"MORPH-10","qty":2,"ward":"CARDIO-2","room":"214"}'
curl -s localhost:8082/pharmacy/queue
curl -s -X POST localhost:8082/pharmacy/orders/<id>/validate -H 'X-User-Id: u-pharma-1'
```

UI: http://localhost:8082/ui/orders/new and http://localhost:8082/ui/pharmacy/queue. In the Artemis console
(http://localhost:8161), the address `doserunner.events` shows the messages, with `eventType`/`traceId` as properties.

## 8. Decisions and fallback

- **Avro for gRPC: kept.** The marshaller works over a real gRPC channel in the tests, so the week-1 Protobuf
  fallback (blueprint §12) is not needed. To be confirmed in `docs/retro-w1.md`.
- **No schema registry**: the fingerprint plus a local `SchemaStore` is enough for the v1/v2 demo, and it means
  one less moving part in the cluster.
- **Who is the user before I2**: `CurrentActor` uses the JWT principal first, then the `X-User-Id` dev header
  (`doserunner.order.actor.user-header-enabled`), then a demo id per role. It is replaced by the JWT subject at I2.
