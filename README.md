# recommender-service — Stage 0 skeleton

Java 21 / Spring Boot 3.3 implementation of the **Stage 0 — PoC / Skeleton**
scope defined in [`../system-design-v2.md`](../system-design-v2.md) §10: a
real, minimal slice of the target-state Recommendations Aggregator (§7.5) —
scatter-gather across the Service1 + Service2 provider adapters described in
the pre-sale case, normalized to a unified model, with partial results if a
provider fails.

## What's in scope (and what isn't)

Per the system design, Stage 0 is deliberately narrow:

- **In scope:** `GET /recommendations/{userId}` (the aggregator, the actual
  customer concern) and `GET /users/{userId}/profile` (just enough
  persistence to drive it without re-typing height/weight/birth date on
  every call).
- **Out of scope** (explicitly deferred to Stage 1+ per system-design-v2.md
  §10): auth, consent, device ingestion, social/loyalty, analytics,
  retry/circuit-breaker resilience beyond timeout + partial results.
- **Deliberately added beyond the literal Stage 0 spec:** H2 + Liquibase
  persistence for `User`/`UserProfile` (Stage 0 originally called for
  UI-entered, non-persisted input). This was a scope decision made with the
  customer stakeholder for this session — see conversation record — to make
  the skeleton demoable without manual re-entry, while keeping recommendation
  results themselves ephemeral (not persisted), consistent with the
  "thin vertical slice" intent.

## Architecture

```
Client
  │
  ▼
RecommendationsController / UsersController      (generated API interfaces,
  │                                                openapi/recommender-api.yaml)
  ▼
RecommendationAggregatorService                  scatter-gather, merge & rank
  │            │
  ▼            ▼
Service1Adapter   Service2Adapter                 ProviderAdapter interface —
  │                 │                              add Service3 = new adapter,
  ▼                 ▼                               zero changes here
Service1Client    Service2Client                  unit conversion, Lambda
  │                 │                              envelope unwrap
  ▼                 ▼
  Service1/Service2 mock endpoints (real HTTP, see openapi/service*-api.yaml)
```

- **`domain/provider`** — one `ProviderAdapter` per external provider. Adding
  Service3 means adding a new `@Component` implementing the interface;
  `RecommendationAggregatorService` picks it up automatically (Spring injects
  `List<ProviderAdapter>`) — the extensibility demo called for in the
  pre-sale case and system-design-v2.md §9.
- **`domain/aggregation`** — scatter-gather via a virtual-thread executor,
  per-call timeout (`recommender.aggregation.overall-timeout`), and partial
  results: a failed or timed-out provider is reported in `providerStatuses`
  without failing the whole request.
- **`domain/user`** — H2/Liquibase-backed profile lookup.
- **`config`** — externalized provider base URLs/timeouts
  (`ProviderProperties`), one `RestClient` per provider.
- **`web`** — RFC 7807 `ProblemDetail` error responses, a correlation-id
  filter (`X-Correlation-Id`) so every log line for a request can be tied
  together.

## OpenAPI-driven codegen

Three specs under `openapi/`, all wired through
`openapi-generator-maven-plugin` in `pom.xml` (runs on `generate-sources`,
no manual step needed):

| Spec | Generates | Used for |
|---|---|---|
| `recommender-api.yaml` | server interfaces + models (`generated.api`, `generated.model`) | our own exposed API — controllers `implements` the generated interfaces |
| `service1-api.yaml` | models only (`generated.service1.model`) | `Service1Client` request/response types |
| `service2-api.yaml` | models only (`generated.service2.model`) | `Service2Client` request/response types |

The provider specs were **not** trusted blindly from the pre-sale case PDF or
even the deployed mock's own `/openapi.json` — both were fetched and
independently verified against the mock's live HTTP behavior (see below),
because the two disagreed with each other.

## A quirk that only showed up by calling the real mock

The mock endpoint at
`https://a2da22tugdqsame4ckd3oohkmu0tnbne.lambda-url.eu-central-1.on.aws`
is a Lambda Function URL deployed **without** a proxy-integration response
mapping. Its HTTP response body is not the documented payload — it's an
envelope:

```json
{"statusCode": 200, "body": "[{\"confidence\": 0.6, \"recommendation\": \"Walk more\"}]"}
```

`body` is itself a JSON-encoded string that has to be parsed a second time.
`Service1Client`/`Service2Client` handle this explicitly
(`Service1LambdaEnvelope`/`Service2LambdaEnvelope` in the generated models).

Separately, the PDF documents Service2's success shape as
`{"recommendations": [...]}`, but the live mock actually returns a **bare
array** inside `body`. `Service2Client.parseBody` tolerates both shapes
defensively rather than picking one and breaking on the other.

Neither discrepancy is guesswork — both were confirmed with `curl` against
the live endpoint before being encoded into the OpenAPI specs and client
code.

## Compliance-driven choices (system-design-v2.md §2, §3, §8)

- **No health/PII payloads in logs.** `GlobalExceptionHandler` and every
  logging statement log identifiers (`userId`, provider name, status,
  latency) — never height/weight/birth date or recommendation content. This
  mirrors the target-state rule "no health data enters telemetry" and the
  audit-log design ("metadata only") even though Stage 0 has no real audit
  log yet.
- **Correlation ID, not request/response body dumping**, for traceability —
  same reasoning: a debug-friendly skeleton that never has to be re-audited
  for accidental PII logging later.

## Running it

```bash
cd recommender-service
mvn spring-boot:run
```

- API: `http://localhost:8080` — e.g. `GET /recommendations/u-1001`,
  `GET /users/u-1001/profile` (seeded users: `u-1001`, `u-1002`, `u-1003`,
  see `src/main/resources/db/changelog/changes/002-seed-users.yaml`)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL
  `jdbc:h2:mem:recommender`, user `sa`, empty password)
- Health: `http://localhost:8080/actuator/health`

## Testing

```bash
mvn test
```

25 tests covering: provider clients (envelope unwrapping, unit conversion,
both Service2 response shapes, network/parse failure handling), adapters
(mapping to the unified model), the aggregator (merge/rank, partial results
on failure, timeout handling, propagating unknown-user errors), the
Liquibase-seeded persistence layer, controller contracts (`MockMvc`), the
global exception handler, the correlation-id filter, and a full
`@SpringBootTest` context-load smoke test.

## Known simplifications vs. target state

These are intentional Stage 0 scope cuts, not oversights — each maps to a
later delivery stage in system-design-v2.md §10:

- No retry or circuit breaker per provider — only a timeout. Target state
  (§9) adds circuit breaking; Stage 0 only needs to prove partial results
  work.
- No caching (ElastiCache in target state) — every call hits the providers.
- No pseudonymization at the provider boundary — target state pseudonymizes
  before any external call; Stage 0 sends raw height/weight/birth date since
  there's no real user PII involved yet (seeded demo data only).
- Recommendation results are not persisted — only `User`/`UserProfile` are.
