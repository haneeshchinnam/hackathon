# Sentinel AML backend

Java 17 / Spring Boot / PostgreSQL backend extending the existing JWT authentication project. Includes customer and account ingestion, synchronous transaction monitoring, configurable rules, ranked alerts, analyst case management, and immutable workflow audit records. No frontend is included.

## Run

1. Install Java 17+ and PostgreSQL; create the `detection` database.
2. Export configuration in your shell (see `.env.example`; Spring does not automatically load `.env`):

   ```sh
   export DB_URL=jdbc:postgresql://localhost:5432/detection
   export DB_USERNAME=postgres
   export DB_PASSWORD='your-local-database-password'
   export JWT_SECRET="$(openssl rand -base64 48)"
   ./gradlew bootRun
   ```

3. Flyway applies migrations automatically. V1/V2 are preserved; V3 adds the AML schema and roles. Existing databases must already have their V1/V2 Flyway history; automatic baselining is disabled to avoid silently skipping schema creation.
4. Open [Swagger UI](http://localhost:8080/swagger-ui/index.html). Machine-readable documentation is at `/v3/api-docs`. Use **Authorize** with your access token.

Register and log in using `/api/v1/auth/register` and `/api/v1/auth/login` (legacy `/api/auth` routes remain available). Registration accepts `{ "username", "email", "password" }`; login accepts `{ "username", "password" }`. Registration creates `ROLE_USER` only. Refresh uses `POST /api/v1/auth/refresh` with `refresh_token: Bearer <refresh-token>`.

Bootstrap an administrator by registering a user, then granting a role through a trusted database session:

```sql
INSERT INTO user_roles(user_id, role)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'your_registered_username'
ON CONFLICT DO NOTHING;
```

Log in again after changing roles. Tokens issued before this update do not acquire new permissions. Old tokens issued before token-type enforcement also require re-login. There is no public role-escalation endpoint.

| Role | Permissions |
| --- | --- |
| USER | Masked customer list and own authentication/profile |
| INGESTOR | Customer/account/transaction ingestion and CSV imports; masked customer list |
| ANALYST | Customer details, accounts, transaction timeline, alerts, case workflows and audit |
| ADMIN | All AML operations, configuration, and safe application-user listing |

The JWT filter now runs before anonymous authentication. Refresh tokens cannot authenticate API requests. Credential fields are excluded from user-list responses. Database and JWT secrets have no source-code defaults. Auth endpoints retain the existing 10 requests/minute limiter; streaming ingestion is not subject to that login throttle.

## API

All AML routes start with `/api/v1`. Requests use camelCase JSON. Query/detail results use the database's snake_case keys; command results use `id`, `status`, `baseAmount`, etc. Errors share `status`, `error`, `message`, `path`, and `timestamp`. Lists use zero-based `page` and `size` (default 50, maximum 200).

| Method | Path | Purpose |
| --- | --- | --- |
| POST / GET | `/customers` | Create / masked customer list |
| GET | `/customers/{id}` | Authorized KYC detail |
| POST / GET | `/accounts` | Create / list, optional `customerId` |
| GET | `/accounts/{id}` | Account detail |
| POST / GET | `/transactions` | Incremental ingestion + synchronous detection / timeline with optional `customerId`, `accountId` |
| GET | `/transactions/{id}` | Transaction detail and rate snapshot |
| POST | `/customers/batch`, `/accounts/batch`, `/transactions/batch` | JSON `{ "records": [...] }`, 1–10,000 records |
| POST | `/imports/customers`, `/imports/accounts`, `/imports/transactions` | Multipart CSV field named `file`, at most 20 MB / 10,000 records |
| GET | `/alerts` | Risk descending queue, optional `status` |
| GET | `/alerts/{id}` | Explanation, rule snapshots and evidence; `evidencePage`, `evidenceSize` |
| PATCH | `/alerts/{id}/disposition` | `{ "status": "IN_REVIEW\|CLEARED\|ESCALATED", "reason": "..." }` (choose one status) |
| GET | `/alerts/{id}/audit` | Immutable transition history |
| POST / GET | `/cases` | Create / list cases |
| GET / PATCH | `/cases/{id}` | Detail / change state or assignment |
| GET | `/cases/{id}/audit` | Case transition history |
| GET | `/config/rules` | Rule configuration |
| PUT | `/config/rules/{code}` | Update rule parameters |
| GET | `/config/exchange-rates` | Currency → INR conversion rates |
| PUT | `/config/exchange-rates/{currency}` | `{ "inrPerUnit": 83 }` |
| GET / PUT | `/config/watchlist` | List / upsert `{ "kind": "COUNTERPARTY", "value": "...", "enabled": true }` |

Creates return 201. Exact transaction replays return 200 with `duplicate: true`; the same transaction ID with different content returns 409. Missing resources return 404, malformed inputs 400, unauthenticated requests 401, and insufficient roles 403. Customer/account IDs cannot be replaced through ingestion.

Customer/account batches are atomic. Structurally invalid JSON batches fail before processing. Validly shaped transaction batches commit each record independently and return 200 with `accepted`, `rejected`, and row-level `results`; inspect that report for failures. `accepted` includes exact idempotent retries. CSV syntax/type errors reject the file before ingestion; customer/account CSV database validation rolls back the whole file. Transaction CSV domain failures produce the same per-record report. Rejections are logged without record contents or PII.

### Transaction example

```json
{
  "id": "TX_000001",
  "accountId": "ACC_000001",
  "amount": 9500,
  "currency": "USD",
  "direction": "OUT",
  "counterparty": "SYNTHETIC RECIPIENT",
  "channel": "BANK",
  "occurredAt": "2026-07-20T10:00:00Z",
  "jurisdiction": "IN"
}
```

Timestamps must include a timezone, may not be in the future, and are stored at PostgreSQL microsecond precision. Transactions cannot predate account opening or postdate its closing date. Historical records for inactive accounts are allowed. Currency and country codes must be uppercase. Counterparty values are normalized only for watchlist matching; the original transaction value is retained.

### CSV mapping

The provided `customers.csv` and `accounts.csv` headers are supported directly. Import customers first, then accounts. Empty optional fields become null; quoted CSV fields are supported.

Customer fields stored: `customer_id`, `first_name`, `last_name`, `date_of_birth`, `email`, `phone_number`, `city`, `state`, `country`, `postal_code`, `occupation`, `annual_income`, `customer_since`, `customer_segment`, `kyc_status`, `risk_rating`, `is_politically_exposed` (`0/1`, `N/Y`, `false/true`).

Account fields stored: `account_id`, `customer_id`, `account_type`, `account_status`, `currency`, `open_date`, `close_date`, `branch_code`, `branch_city`, `current_balance`, optional `risk_rating` (defaults to LOW for the supplied CSV, which has no account risk column). Additional source demographics, device, card and marketing columns are ignored; the import does not retain a full source-file archive.

Transaction CSV headers: `transaction_id,account_id,amount,currency,direction,counterparty,channel,timestamp,jurisdiction`.

## Detection and configuration

Rules are read from PostgreSQL on ingestion, so configuration changes apply without redeployment. Every transaction stores INR and USD equivalents calculated using one rate-table snapshot. Seeded exchange rates are **demonstration values**, not live market data. Set operational rates before importing data. INR must remain 1; unknown currencies are rejected.

| Code | Default behavior | Weight |
| --- | --- | --- |
| THRESHOLD | Single transaction ≥ USD 10,000 | 40 |
| STRUCTURING | At least 3 same-account transactions, USD 9,000 inclusive to 10,000 exclusive, within 24h | 35 |
| RAPID_MOVEMENT | Aggregate outflows strictly after a deposit reach at least 80% of that deposit, within 48h | 40 |
| HIGH_RISK | Exact jurisdiction or case-insensitive counterparty watchlist match, any amount | 60 |
| BEHAVIORAL | Customer-wide daily INR value or count exceeds 3× the preceding 90-calendar-day average | 30 |
| ROUND_NUMBER | At least 3 same-account USD-equivalent multiples of 1,000 within 24h | 20 |

Structuring uses the continuous interval `[9000, 10000)` to include cent amounts just below the reporting threshold. Rapid movement is a screening heuristic comparing each deposit to later outflows, not proof that specific funds were reused. Behavior uses UTC days, divides by 90 including quiet days, excludes the current day from the baseline and requires at least one historical transaction. No-history customers are evaluated by the other rules. Round-number detection uses normalized USD amounts.

Rule update example for `PUT /config/rules/STRUCTURING`:

```json
{"enabled":true,"threshold":9000,"secondaryThreshold":10000,"windowHours":24,"minimumCount":3,"weight":35}
```

`secondaryThreshold` applies to the structuring upper bound; `threshold` is the ratio for rapid movement, multiplier for behavior, and amount for threshold/round-number rules. `windowHours` and `minimumCount` apply to structuring and round-number rules; rapid movement uses `windowHours`. The behavioral baseline remains fixed at 90 days. Unused fields are retained in the common configuration shape. HIGH_RISK screening cannot be disabled; individual watchlist entries can be enabled/disabled. Config edits are audited and apply to subsequent evaluations, not an automatic full-history rescan.

Repeated hits aggregate into one active customer alert with distinct rule weights summed and capped at 100. Evidence is deduplicated by transaction ID. This deliberately groups active patterns across the customer's accounts. After an alert is cleared/escalated, fresh triggering activity can create another alert. Historical evidence may appear again if later activity still satisfies a rolling pattern. Disposed alerts remain unchanged.

Ingestion locks the customer row and commits the transaction, rule results, alert evidence and audit together. Late-arriving events reevaluate affected later anchors (up to 91 days for rolling daily baselines). Retried exact transaction IDs do not re-run detection. Late replay cost grows with customer history; very large histories require further incremental aggregate optimization.

## Demo: ingestion → detection → case disposition

Use an ADMIN access token in `$TOKEN` and the running server at `$BASE`:

```sh
export BASE=http://localhost:8080/api/v1
# Add a deliberately synthetic watchlist entry (no sanctions data is bundled).
curl -f -X PUT "$BASE/config/watchlist" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"kind":"COUNTERPARTY","value":"SYNTHETIC WATCHLIST PARTY","enabled":true}'

curl -f "$BASE/imports/customers" -H "Authorization: Bearer $TOKEN" -F file=@data/customers.csv
curl -f "$BASE/imports/accounts" -H "Authorization: Bearer $TOKEN" -F file=@data/accounts.csv
curl -f "$BASE/imports/transactions" -H "Authorization: Bearer $TOKEN" -F file=@data/transactions.csv
curl -f "$BASE/alerts" -H "Authorization: Bearer $TOKEN"
```

Expect structuring for `DEMO_C001`, rapid movement for `DEMO_C002`, and a watchlist hit for `DEMO_C003`. Import into an empty demo dataset once; customer/account reimports return 409. Transaction retries are safe.

Use an alert ID from the response to create a case:

```json
{"title":"Review synthetic pattern","alertIds":[1],"assignedTo":null}
```

POST that body to `/cases`; then PATCH the alert's `/disposition` with `{"status":"IN_REVIEW","reason":"Investigation started"}` followed by `{"status":"CLEARED","reason":"Synthetic activity verified"}` or ESCALATED. PATCH the case with `{"status":"CLOSED","reason":"Review completed","assignedTo":null}`. Closed cases and disposed alerts are terminal. Case closure does not silently disposition its linked alerts. Audit endpoints show actor, timestamp, old/new state and reason.

## Architecture and tests

The backend uses the same layer packages for authentication and AML: `controller` handles HTTP and role checks, `dto` validates write inputs, `service` handles authentication, ingestion, workflows, and detection, `model` contains JPA entities, and `repository` provides persistence and history/configuration queries. SQL read projections prevent lazy-entity serialization and allow masking PII in lists. See [schema and ERD](docs/schema.md).

```sh
# Fast deterministic rule tests; DB-backed tests are skipped without explicit opt-in.
./gradlew test

# Use an isolated, disposable PostgreSQL database. Migrations and synthetic fixtures persist.
export AML_INTEGRATION_TESTS=true
export AML_PERFORMANCE_TEST=true
export DB_URL=jdbc:postgresql://localhost:5432/sentinel_test
export DB_USERNAME=your_test_db_user
export DB_PASSWORD=your_test_db_password
export JWT_SECRET="$(openssl rand -base64 48)"
./gradlew test --rerun-tasks
```

Tests cover threshold/window boundaries, customer-wide behavioral calculations, watchlist matches, split outflows, disabled rules, foreign keys, duplicate IDs, rollback, concurrency, late events, immutable audit, case disposition, CSV parsing, HTTP authorization and OpenAPI. The opt-in performance test ingests 10,000 records across 100 customers with 100 structuring patterns and asserts completion under two minutes. Local measured ingestion was about 5.5 seconds on isolated PostgreSQL, excluding setup and network request overhead; this is a synthetic prototype benchmark, not a production throughput guarantee.

Reports: `build/reports/tests/test/index.html`. Integration/benchmark opt-ins should only point at a test database. No external watchlist feed, FX provider, Kafka broker, or frontend is required. REST provides incremental ingestion. The current workspace did not contain Git metadata; no commit or remote repository was created.
