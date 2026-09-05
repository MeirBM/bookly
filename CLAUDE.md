# Bookly — working rules

Multi-tenant appointment booking. Java 21 + Spring Boot 3 modular monolith, PostgreSQL + Flyway,
Redis, Next.js + TypeScript. Read `docs/framing.md` before proposing work, and the current
`docs/spec/turn-N.md` before writing any of it.

## The five rules that are never traded away

1. **Never trust a `businessId` from the client body.** Tenant access is decided from the
   authenticated principal's row in `business_members`, every time.
2. **Never let application code be the only thing preventing a double booking.** The database
   constraint is the guarantee; service code is convenience on top of it.
3. **Never generate availability from a stored slot table.** Slots are computed per request from
   hours, services, appointments and blocked time.
4. **Never write a test by reading the implementation.** Tests come from the specification. A test
   that mirrors the code proves only that the code agrees with itself.
5. **Never commit a secret.** History is permanent and sits in every clone. A committed key is a
   compromised key and must be rotated, not deleted.

## Before you change anything

Investigate first: read the module you are about to touch and its tests. If the change spans more
than one module, say so and stop for confirmation rather than widening silently. Work the loop —
understand, plan, implement, test, review — and do not begin implementing while the plan is still
being discussed.

If a correction is given, write it into this file as a rule. A complaint repeated three times is a
rule that was never written down.

## Backend

- Packages by domain module under `com.bookly`: `auth`, `user`, `business`, `employee`, `service`,
  `customer`, `appointment`, `common`. A module owns its entities, repositories, services, DTOs and
  controller. Cross-module calls go through the other module's service, never its repository.
- Do not create a package for a feature that does not exist yet.
- **Controllers are thin**: validate, delegate, map to a response. No business rule in a controller.
- **Never return a JPA entity from a controller.** DTOs (Java `record`s) in and out. Entities do
  not leave the service layer.
- `@Transactional` on service methods that write, not on repositories or controllers. Keep
  transactions short; never hold one across an HTTP call to anything external.
- No `Optional` fields on entities, no bidirectional relations unless both directions are used, no
  `CascadeType.REMOVE` on tenant-owned data.
- Lazy-load by default. If a query needs joins, write the query — do not fix an N+1 with `EAGER`.
- Constructor injection only. No field `@Autowired`.
- No abstraction with a single implementation and no second one planned. No interface named
  `XService` with exactly one `XServiceImpl`.

## Multi-tenancy

- Tenant-scoped routes are `/api/businesses/{businessId}/...` and carry
  `@PreAuthorize("@tenantGuard.canAccess(#businessId)")`.
- Every repository method touching tenant-owned data takes `businessId` as a parameter and filters
  on it. There is no unscoped `findById` for `Appointment`, `Employee`, `Service` or `Customer`.
- A missing or non-member business yields 403, and the response body distinguishes nothing about
  whether the business exists.
- Every new tenant-scoped endpoint gets a case in the tenant-isolation test suite in the same
  commit that introduces it.

## Database and Flyway

- Every schema change is a new versioned migration. **Never edit a migration that has been
  committed** — add the next one.
- Migrations are named `V{n}__snake_case_description.sql`, forward-only, no `DROP` of a column
  holding data without an explicit decision recorded in the commit message.
- Timestamps are `timestamptz`. Never `timestamp`. Never store local time.
- Declare constraints in the schema, not only in Java: foreign keys, unique constraints, check
  constraints, and the appointment overlap exclusion constraint.
- Index every foreign key and every column used to filter a tenant query.

## Time

- Instants are `Instant`/`OffsetDateTime` in Java and `timestamptz` in Postgres.
- Business-local reasoning uses `ZonedDateTime` with the business's IANA zone from
  `businesses.timezone`. Never `LocalDateTime.now()`, never the server's default zone.
- Adding a duration across a DST boundary is done in the zone, not by adding seconds to an instant.
- `AvailabilityCalculator` is a pure function: inputs in, slots out, no repository, no clock lookup
  — the clock is passed in. This is what makes the hard logic testable without a database.

## Appointment rules

- A slot is offered only if the service fits entirely inside a free window on an employee who is
  linked to that service and working at that time.
- A booking writes to `appointment_status_history` on every status change, including creation.
- Cancellation frees the slot; it does not delete the appointment.
- Rescheduling is one transaction: it either moves or leaves the original untouched.
- The overlap constraint applies to `PENDING` and `CONFIRMED` only, so cancelled appointments do
  not block the time.
- A constraint violation surfaces as HTTP 409 with a body that tells the caller to refresh the
  slots. It is never swallowed and never retried silently.

## REST, validation, errors

- Plural nouns, no verbs in paths. 201 with a `Location` header on create; 204 on delete —
  and **declare them**: springdoc assumes 200 unless told otherwise, so an undocumented
  create advertises a status it does not return and any client generated from the document
  is wrong.
- `@Valid` on every request body, Jakarta constraints on the DTO. Validation lives on the DTO, not
  in the controller body.
- One `@RestControllerAdvice` maps exceptions to a single error shape: `code` and `message`
  always, plus `fieldErrors` **only on a validation failure** — an empty map on every other
  error is noise a client has to ignore. A `code` is a stable string a client can branch on.
- An error message never contains a stack trace, a SQL fragment, or whether an email is registered.
- Every endpoint appears in the OpenAPI document with its response codes.

## Redis

Rate limiting and caching only. PostgreSQL is the source of truth. Do not add a Redis lock to solve
a problem the database already solves with a constraint. Anything cached must be correct if the
cache is empty.

## Testing

- Unit tests for domain logic, with no Spring context — the availability engine especially.
- Testcontainers for anything touching Postgres or Redis. No H2: a test against a different database
  than production is evidence about the wrong system.
- The tenant-isolation and availability suites are written by an agent given the spec and never the
  implementation (see `docs/coordination.md`).
- Coverage is not evidence. A named test that fails when the behaviour breaks is evidence.
- If a bug is found, the regression test comes first and must fail before the fix lands.

## Frontend

- Server state through TanStack Query; forms through React Hook Form with Zod schemas. No manual
  `useEffect` fetching.
- Types for API payloads are generated from the OpenAPI document, not hand-copied.
- Every screen that loads data renders four distinguishable states: loading, empty, error, and
  content. A failure looks like a failure — never a blank list and never a default value.
- No secret, key or privileged decision in browser code. The browser is untrusted.

## Git

- Commit before starting any non-trivial change, so there is a way back.
- Atomic commits: one logical change. The diff shows *what*, so the message says **why** — it should
  still explain the change in six months.
- One branch per spiral turn, merged by PR with CI green. The turn's spec is committed before its
  first implementation commit.
- The pre-commit hook scans for secrets and runs unit tests. Do not bypass it with `--no-verify`.

## Logging

`slf4j`, parameterised, no string concatenation. Never log a password, token, or full request body.
Log the business id and appointment id on booking paths — an audit trail turns a mystery into a
traceable case.

---

## The five rules again, because attention falls in the middle

1. Tenant access comes from `business_members`, never from a client-supplied `businessId`.
2. The database constraint prevents double booking; service code is not the guarantee.
3. Availability is computed, never stored as slots.
4. Tests come from the specification, never from reading the code.
5. No secret enters git, ever.
