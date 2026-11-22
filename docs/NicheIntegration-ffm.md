# Niche integration — FFM (Foreign Function & Memory API)

Owner: **D** · Modules: `commons-native`, `custody-service`, `native/` · Status: week 1, day 3

FFM (`java.lang.foreign`, final since Java 22) lets Java call C functions and handle native memory **without JNI**:
you don't write C glue, run `javah`, or build a per-platform JNI library. DoseRunner uses it twice:

| Native library | Used for | Status |
|---|---|---|
| **libsodium** (`libsodium.so.23`) | Ed25519 signatures + BLAKE2b hashes of the **custody ledger** | ✅ bound, tested, used by custody-service |
| **libroute** (`native/libroute.c`, ours) | A* path-finding for dispatch | 🟡 C library + C tests done; Java binding next |

---

## 1. Where the code is

| File | Role |
|---|---|
| [`commons-native/.../Sodium.java`](../commons-native/src/main/java/tn/rnu/eniso/doserunner/nativelib/Sodium.java) | The FFM binding: 5 downcall handles, `byte[]` in / `byte[]` out API |
| [`commons-native/.../SodiumInitCheck.java`](../commons-native/src/main/java/tn/rnu/eniso/doserunner/nativelib/SodiumInitCheck.java) | Day-3 gate: smallest possible FFM call (`sodium_init()`) |
| [`custody-service/.../bo/CustodyChain.java`](../custody-service/src/main/java/tn/rnu/eniso/doserunner/custody/bo/CustodyChain.java) | Hash-chain rules: append + verify (pure Java, calls `Sodium`) |
| [`custody-service/.../bo/LinkCodec.java`](../custody-service/src/main/java/tn/rnu/eniso/doserunner/custody/bo/LinkCodec.java) | Canonical bytes that get signed and hashed |
| [`custody-service/.../facade/CustodyFacade.java`](../custody-service/src/main/java/tn/rnu/eniso/doserunner/custody/facade/CustodyFacade.java) | `record()` and `verify(orderId)` (transactions, entity ↔ business mapping) |
| [`custody-service/.../db/migration/V1__custody_event.sql`](../custody-service/src/main/resources/db/migration/V1__custody_event.sql) | `custody_event` table + append-only trigger |
| [`custody-service/.../templates/audit.html`](../custody-service/src/main/resources/templates/audit.html) | `/ui/audit/{orderId}`: timeline + VALID / INVALID badge |
| [`native/libroute.c`](../native/libroute.c), [`libroute.h`](../native/libroute.h), [`Makefile`](../native/Makefile) | A* in C |
| [`deploy/compose/Dockerfile.native`](../deploy/compose/Dockerfile.native) | Image with `libsodium23`, `libroute.so`, `--enable-native-access` |

---

## 2. How one native call is wired

Four steps, all in the `Sodium` constructor:

```java
Linker linker = Linker.nativeLinker();                                        // 1. the platform's C calling convention
SymbolLookup lib = SymbolLookup.libraryLookup("libsodium.so.23", Arena.global()); // 2. dlopen() the library
MemorySegment fn = lib.find("crypto_sign_verify_detached").orElseThrow();       // 3. dlsym() the function
MethodHandle verify = linker.downcallHandle(fn,                                  // 4. describe the C signature
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
```

…and a call:

```java
try (Arena arena = Arena.ofConfined()) {                       // native memory for this call only
    MemorySegment sig = arena.allocateFrom(JAVA_BYTE, signature); // copy byte[] → C buffer
    ...
    int rc = (int) verify.invokeExact(sig, m, (long) message.length, pk);
}                                                               // freed here, even on exception
```

### C → Java type mapping we rely on (Linux x86-64 / aarch64, LP64)

| C type | FFM layout | Java type at `invokeExact` |
|---|---|---|
| `int` | `JAVA_INT` | `int` |
| `size_t` | `JAVA_LONG` | `long` |
| `unsigned long long` | `JAVA_LONG` | `long` (lengths never exceed 2⁶³) |
| `unsigned char *`, `unsigned long long *` | `ADDRESS` | `MemorySegment` (`MemorySegment.NULL` for an optional pointer) |

`invokeExact` needs the **exact** Java types: `message.length` is an `int`, so it must be cast to `(long)`, and the
result must be cast to `(int)`. Without the casts you get `WrongMethodTypeException` at run time, not a compile error.

### The 5 bound functions

| C function | Java method | Notes |
|---|---|---|
| `int sodium_init(void)` | `init()` | 0 = initialised now, **1 = already initialised**, -1 = failure. Called once when `Sodium.get()` loads the library |
| `crypto_sign_keypair(pk, sk)` | `generateKeyPair()` | pk 32 bytes, sk 64 bytes (seed ‖ pk) |
| `crypto_sign_detached(sig, siglen_p, m, mlen, sk)` | `sign(message, secretKey)` | `siglen_p` = `NULL` (always 64) |
| `crypto_sign_verify_detached(sig, m, mlen, pk)` | `verify(signature, message, publicKey)` | wrong-length inputs return `false` **before** reaching C |
| `crypto_generichash(out, outlen, in, inlen, key, keylen)` | `hash(input)` | BLAKE2b-256, no key (`NULL`, 0) |

---

## 3. Memory rules

| Rule | Why |
|---|---|
| Library loaded into `Arena.global()`, once per JVM (`Sodium.get()`, double-checked locking) | The library must stay mapped while method handles exist. Closing that arena would unload it under our feet |
| Every call uses `Arena.ofConfined()` in try-with-resources | Deterministic free, no GC involvement, no leak on exception. *Confined* = only the creating thread can touch it, so no data races, and each call is independent, so `Sodium` is thread-safe |
| No `MemorySegment` leaves `Sodium`: the API is `byte[]` only | Callers can't use a segment after its arena closed (that would throw `IllegalStateException`), and they don't need to know FFM exists |
| Native copies of the secret key are zeroed (`fill(0)`) before the arena closes | Freed memory is not cleared by the allocator; don't leave key bytes behind |
| `KeyPair` clones its arrays and redacts the secret key in `toString()` | Keeps the key out of logs and away from aliasing bugs |

---

## 4. What custody-service does with it

Each custody step of an order (placed, validated, robot assigned, pickup, delivery, abort) becomes one **link**:

```
signature(n) = Ed25519_sign(sk, signedBytes(n))           signedBytes includes prevHash(n)
linkHash(n)  = BLAKE2b-256(signedBytes(n) ‖ signature(n))
prevHash(n)  = linkHash(n-1)                             prevHash(1) = 32 zero bytes
```

`signedBytes` ([`LinkCodec`](../custody-service/src/main/java/tn/rnu/eniso/doserunner/custody/bo/LinkCodec.java)) is a
fixed, length-prefixed binary encoding with a domain tag `doserunner.custody.v1`, so two different links can never
produce the same bytes (we test that `null` and `""` encode differently).

Entity `CustodyEvent` / table `custody_event`: `orderId, seq, type, actorId, robotId, payloadHash, prevHash, signature, ts`
plus `eventId` (unique: the same Artemis event delivered twice creates one link, ADR 0001 #1). `(order_id, seq)` is
unique too: if two appends race, one transaction fails and its message is redelivered.

**`CustodyFacade.verify(orderId)`** reads the chain in `seq` order and checks, for every link: `seq` has no gap → it
belongs to the order → `prevHash` equals the recomputed hash of the previous link → the Ed25519 signature is valid.
It stops at the **first broken link** and returns `{valid, links, brokenAtSeq?, reason?}`.

| Tampering (tested) | Detected at |
|---|---|
| Change any field of link *k* (actor, robot, …) | *k* (signature) |
| Flip one bit of a stored hash or signature | *k* |
| Re-sign link *k* with another key | *k* (signature) |
| Delete a middle link | the next link (`expected seq k`) |
| Replace `prevHash` | *k* |
| `UPDATE` / `DELETE` without disabling the trigger | refused by Postgres (`append-only`) |
| **Delete the last link(s)** | ❌ **not detected**: see §8 |

Endpoints: `GET /custody/orders/{id}`, `GET /custody/orders/{id}/verify`, `GET /custody/public-key`, and the page
`/ui/audit/{orderId}`. There is still no write endpoint: links come only from events (ADR 0001 #12). The Artemis
listener that calls `record()` is the next step. Until then, run with profile `demo` to seed `ORD-DEMO-1`.

**Signing key:** `custody.signing.key-file` (default `secrets/custody-ed25519.sk`, gitignored; in compose it's on the
`custody-keys` volume). If it's missing, a new key is generated and a WARN is logged: chains signed with the old key
will no longer verify. Set `custody.signing.generate-if-missing=false` anywhere that must not happen.

**Timestamps** are cut to **milliseconds** before signing: Postgres stores microseconds and Java's `Instant.now()` has
nanoseconds, so without that a link would fail verification after a round trip through the database.

---

## 5. Running it

**Requirements:** JDK 25 and `libsodium23` (Debian/Ubuntu: `apt-get install libsodium23`). The binding looks for
`libsodium.so.23`; override with `-Ddoserunner.sodium.lib=/path/to/libsodium.so` (or `libsodium.dll` on Windows).

**`--enable-native-access=ALL-UNNAMED`:** FFM downcalls are *restricted methods*. Without the flag the JVM prints a
warning (a future JDK will make it an error). It's set in the `argLine` of `commons-native` and `custody-service` (tests)
and in the `ENTRYPOINT` of `Dockerfile.native`.

### Day-3 gate

```bash
docker build -f deploy/compose/Dockerfile.native --target ffm-smoke -t doserunner/ffm-smoke .
docker run --rm doserunner/ffm-smoke
# sodium_init() = 0
```

The image is `eclipse-temurin:25.0.5_8-jre` + `libsodium23` + the `commons-native` jar, nothing else.
`SodiumInitCheck` deliberately does **not** go through `Sodium.get()`: that already calls `sodium_init()`, and a
second call returns `1`.

### Tests

```bash
./mvnw -pl custody-service -am verify     # needs libsodium23 + Docker (Testcontainers Postgres 16.16)
make -C native test                        # C tests for libroute
```

| Test class | What it proves |
|---|---|
| `SodiumTest` (8) | sign → verify OK; **flip one byte of the message → verify KO**; flipped signature / other key / wrong lengths → KO; empty message; BLAKE2b known answer; key reload from the secret key |
| `CustodyChainTest` (9) | chain rules without a database: every tampering case in §4, empty chain, cross-order append refused, canonical encoding |
| `CustodyFacadeTest` (9) | same against **real Postgres** + Flyway: tampering done in SQL after disabling the trigger, duplicate event, trigger refusal, ms precision after a reload, the tail-deletion limitation |
| `CustodyServiceApplicationSmokeTest` (7) | over HTTP: health, 404 with traceId, `/verify`, chain, public key, audit page **VALID → INVALID** after tampering, search redirect |

Without libsodium, the `commons-native` and `CustodyChainTest` tests are **skipped** (not failed) so a Windows laptop
can still build. CI and the Docker scripts set `DOSERUNNER_NATIVE_REQUIRED=true`, which turns a missing library into a
failure. The custody-service Spring tests always need libsodium and Docker.

---

## 6. libroute (C side done)

```c
int route(int w, int h, const uint8_t* grid, int sx, int sy, int gx, int gy, int* out_xy, int max_len);
```

A* on a 4-connected grid (`0` = free), binary-heap open set with lazy deletion, Manhattan heuristic (admissible →
optimal path), deterministic tie-breaking, no global state (re-entrant). Returns the number of points written to
`out_xy` as `x0,y0,…` start → goal, `0` = no path, `-1` bad args, `-2` buffer too small, `-3` out of memory.
`make test` checks: open grid, wall detour, unreachable goal, start = goal, small buffer, bad arguments, and a
200×199 serpentine maze of known optimal length. Built with `-Werror`, only `route` exported.

**Next (Java side):** `Route` in `commons-native`. The grid goes in as `arena.allocateFrom(JAVA_BYTE, grid)`, `out_xy`
as `arena.allocate(JAVA_INT, 2L * maxLen)`, read back with `toArray(JAVA_INT)`; then the **upcall** variant, where C
calls a Java `isBlocked(x, y)` through `Linker.upcallStub` (live obstacles from robot telemetry); then a JMH
benchmark against a pure-Java A*.

---

## 7. Why FFM rather than JNI or a Java crypto library

| | FFM | JNI | Pure Java (JCA `Ed25519`, BouncyCastle) |
|---|---|---|---|
| C glue to write/compile | none for libsodium | a `.c` wrapper per function + per-platform build | none |
| Signature declared | in Java (`FunctionDescriptor`) | in C headers generated by `javac -h` | n/a |
| Memory | explicit arenas, bounds-checked segments | manual, a C bug can crash the JVM | GC |
| Niche requirement of the course | ✅ | ❌ | ❌ |

Honest note: Java 15+ has Ed25519 in the JCA, so we don't need libsodium for crypto alone. It's here to exercise FFM
on a real, well-known C API, with libroute as the "our own C code" case (including upcalls, which the JCA can't show).

---

## 8. Limits and pitfalls (know these before a demo)

- **Tail truncation is undetectable.** Deleting the newest link(s) leaves a shorter chain that verifies
  (`knownLimitationDeletingTheTailIsNotDetected`). Fix: anchor the head hash outside this database (publish it on an
  event, or have a second party countersign it).
- **Key and ledger share a host.** An insider with the key file *and* DB rights can rewrite and re-sign a whole chain.
  The trigger only stops accidents. Fix: keep the key outside the service (KMS/HSM) or anchor the head hashes.
- **The signer is the service, not the human.** `actorId` is what order-service *said*. The ledger proves "custody-service
  recorded this, and nobody changed it since".
- **One key, no rotation yet.** `verify` uses the current public key. Rotation needs a `key_id` per link.
- **The payload itself is not stored** (only its hash, so no patient data in the ledger), so `payloadHash` can be
  checked only by whoever still has the original event bytes.
- **`sodium_init()` returns 1 the second time**: don't treat "not 0" as failure, only `< 0`.
- **`invokeExact` is strict about types**: see §2.
- **`Arena.ofConfined()` is single-thread**: never hand a confined segment to another thread (we never do: segments
  don't leave the method).
- Library name `libsodium.so.23` is the Debian/Ubuntu soname; Alpine/macOS/Windows differ, so use the system property.
