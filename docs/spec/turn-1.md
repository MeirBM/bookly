# Turn 1 — Foundation, Authentication, Tenant Isolation

> Module 10 specification. Written before the turn's first implementation commit. The five parts
> are goal *and reason*, testable success criteria, architectural guidance, validation approach, and
> known pitfalls. This document is the deliverable; the code is generated from it, and when
> something is wrong it is rewritten here first.

---

## 1. Goal, and the reason for it

**Goal.** A person can register, create a business, and hold a session across token refresh. Every
route that touches a business's data admits only members of that business.

**Reason.** Bookly puts every business's customer list, staff roster and revenue in the same tables.
Tenant isolation is therefore not a feature of this turn — it is the property that makes every later
turn safe to build. It is also the one defect class that passes every honest test: a screen fetches
a business by the id it was given, the owner tries it, it works, and nobody checked that the
business was theirs. Isolation is built and evidenced first because retrofitting it means auditing
every endpoint written in the meantime.

The reason matters for the forks this spec does not anticipate. When a later decision is ambiguous,
resolve it toward *the caller proves membership*, not toward *the caller supplies context*.

---

## 2. Testable success criteria

Each resolves to one true/false answer, and each names the test that decides it.

| # | Criterion | Decided by |
|---|---|---|
| 1.1 | `docker compose up` brings up postgres, redis, backend and frontend; `GET /actuator/health` returns 200 with status `UP` | manual, recorded in the turn audit |
| 1.2 | `POST /api/auth/register` with a new email returns 201 and creates exactly one `users` row | `AuthFlowIT.registersNewUser` |
| 1.3 | Registering an email that already exists returns 409 and creates no second row | `AuthFlowIT.rejectsDuplicateEmail` |
| 1.4 | `POST /api/auth/login` with correct credentials returns an access token and a refresh token | `AuthFlowIT.loginReturnsTokenPair` |
| 1.5 | Login with a wrong password returns 401, and the response body is byte-identical to the body returned for an unknown email | `AuthFlowIT.loginFailureDoesNotRevealAccountExistence` |
| 1.6 | `POST /api/auth/refresh` returns a new token pair, and the presented refresh token is thereafter rejected with 401 | `RefreshRotationIT.rotatedTokenIsRejectedOnReuse` |
| 1.7 | A refresh token reused after rotation revokes every token in **the family that token belongs to** (not every family of that user; a second login starts its own family and is unaffected), and does so **even when the two refreshes arrive concurrently** | `RefreshRotationIT.reuseRevokesFamily` |
| 1.8 | An expired access token is rejected with 401 on every authenticated route | `AuthFlowIT.expiredAccessTokenRejected` |
| 1.9 | Stored passwords are BCrypt hashes; the plaintext appears in no column of any table, in no `com.bookly` log line at any level, and in no log line at all at INFO or above. (Below INFO the servlet container echoes the raw request body by construction, so a stricter claim would be unsatisfiable rather than merely unmet.) | `PasswordStorageIT.passwordIsNeverStoredOrLoggedInPlaintext` |
| 1.10 | A user authenticated against Business A receives 403 on every tenant-scoped route of Business B | `TenantIsolationIT` — one parameterised case per registered route |
| 1.11 | A `businessId` supplied in a request **body** is *ignored*, not rejected: the request still succeeds and the field has no effect on which business is read or written | `TenantIsolationIT.bodyBusinessIdIsIgnored` |
| 1.12 | A request for a business that does not exist and one for a business the caller is not a member of return the same status, headers and body bytes. Response *timing* is out of scope for this turn: the two share a code path, and defending a timing oracle properly needs measurement this turn cannot supply | `TenantIsolationIT.absentAndForbiddenAreIndistinguishable` |
| 1.13 | `POST /api/businesses` creates a business, makes the creator its `BUSINESS_OWNER`, and assigns a unique slug | `BusinessCreationIT.creatorBecomesOwner` |
| 1.14 | Two businesses cannot hold the same slug | `BusinessCreationIT.slugIsUnique` |
| 1.15 | Every timestamp column added in this turn is `timestamptz` | `SchemaConventionsIT.allTimestampsAreTimestamptz` — reads `information_schema` |
| 1.16 | The OpenAPI document at `/v3/api-docs` lists every route added in this turn with its response codes | `OpenApiIT.documentsEveryRoute` |
| 1.17 | The frontend redirects an unauthenticated visit to `/dashboard` to `/login` | `dashboard.spec.ts` |
| 1.18 | CI runs the full suite on the pull request and is green | the linked run in `docs/audit/turn-1.md` |
| 1.19 | The secret scan runs over the full history, not only the tip, and finds nothing | the same CI run |
| 1.20 | The OpenAPI document declares how a caller authenticates, not only each route's status codes | `OpenApiIT.documentsHowACallerAuthenticates` |
| 1.21 | No DTO carrying a credential — **request or response** — exposes it through `toString()`. Spring logs handler return values as well as arguments, so a response object leaks by the same mechanism as a request one | `CredentialMaskingTest` |
| 1.22 | The unauthenticated `/api/auth/*` endpoints are rate limited per caller, and a failed login is recorded in the log without naming the account | `AuthRateLimitIT` |
| 1.23 | A malformed request — a non-UUID path variable, an unparseable body, an unsupported method, an unknown path — returns 4xx, not 500, and writes no stack trace | `MalformedRequestIT` |
| 1.24 | The OpenAPI document and Swagger UI are not served unless explicitly enabled | `ApiDocsExposureIT` |

**Not claimed by this turn:** password reset, email verification, roles beyond `BUSINESS_OWNER`,
and any business data beyond the business record itself. Those are turn 2 or the out-of-scope list.

---

## 3. Architectural guidance

Boundaries only; the interior is the implementer's.

Tenant-scoped routes take the shape `/api/businesses/{businessId}/...` and are authorised by a
single `TenantGuard` bean consulted from `@PreAuthorize`, so that there is exactly one place where
membership is decided and exactly one place to audit. Repositories touching tenant-owned data accept
`businessId` as a parameter and filter on it — the guard is the gate, the filter is the depth behind
it, and neither alone is the design. Authentication state lives entirely in the tokens plus the
`refresh_tokens` table; no HTTP session, because the same API must serve Android and iOS clients
that have no cookie jar.

**Slug normalisation**, left undefined in the first version of this document and therefore untestable
at its edges: strip accents to ASCII, lowercase, replace every run of non-alphanumeric characters
with a single hyphen, trim leading and trailing hyphens, truncate to 60 characters, then trim a
trailing hyphen again — truncation can land mid-hyphen, and a trailing hyphen violates the
`businesses_slug_shape` CHECK, which would surface as an insert failure rather than a validation
error. A name that normalises to nothing — one written entirely in a non-Latin script, say — becomes
`business`. Collisions take the suffix `-2`, `-3`, and so on, **appended after truncation**, so a
slug may reach 62 characters; shortening the base to hold 60 was the alternative, and appending was
chosen because it keeps a business's slug a stable prefix of its name rather than silently changing
where the name is cut when an unrelated business registers. Transliteration is explicitly *not*
attempted: it needs a per-language table, and getting a business's own name subtly wrong in its
public URL is worse than a neutral slug.

**Redis** holds the rate-limit counters and nothing else. It is the first justified use in the
project: the count must be shared across instances and may be lost without harm. The limiter fails
*open* — if Redis is unreachable the request proceeds — because refusing every login while a cache
is down turns a cache outage into a total outage, and this control mitigates an attack rather than
protecting correctness.

---

## 4. Validation approach

Named before building, so it cannot be chosen afterwards to fit what was built.

- **Unit**, no Spring context: token creation, expiry and signature verification; slug generation.
- **Integration**, Testcontainers against real PostgreSQL 16 and Redis: everything in the table
  above whose test name ends in `IT`. No H2 — a test against a different database than production is
  evidence about the wrong system.
- **The tenant-isolation suite is written by `spec-test-writer` from this document and the OpenAPI
  contract alone**, with no access to `backend/src/main`. Its commit is recorded in the turn audit.
  This is the one suite where a test written by reading the implementation would be worthless: the
  implementation is precisely what is under suspicion.
- `TenantIsolationIT` enumerates routes by reading Spring's `RequestMappingHandlerMapping` rather
  than by a hand-maintained list, so a new tenant-scoped route that nobody remembered to cover fails
  the build instead of passing unnoticed.
- **Gate:** CI must be green before merge. Criterion 1.18 is not satisfied by a local run.

---

## 5. Known pitfalls

The warnings that would be given to a colleague starting this turn.

1. **`@PreAuthorize` silently does nothing** if method security is not enabled, or if the annotated
   method is called from inside the same bean — the proxy is bypassed and the check never runs. The
   test that proves isolation must go through HTTP, never through a direct service call.
2. **A 403 that leaks existence.** Returning 404 for an absent business and 403 for a forbidden one
   tells an attacker which business ids are real. Both must be indistinguishable — criterion 1.12
   exists because this is the natural thing to write and it is wrong.
3. **Refresh rotation without reuse detection** is a rotation in name only. If a stolen token still
   works once, theft is undetectable; reuse must revoke the family.
4. **Spring Security's default `UserDetailsService` message** distinguishes "user not found" from
   "bad credentials" in the response. Criterion 1.5 fails by default and has to be made to pass.
5. **Testcontainers needs a container runtime**, and this machine has Colima rather than Docker
   Desktop. If integration tests cannot find a socket, `DOCKER_HOST` and
   `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` are the cause, not the tests.
6. **Flyway will happily run a migration that the JPA entities disagree with.** Set
   `ddl-auto: validate` so the mismatch fails at startup rather than at the first query.
7. **The slug is user-influenced.** Generated from the business name, so it needs a deterministic
   normalisation and a collision suffix, or two businesses named "Studio" race for the same slug.
8. **BCrypt is deliberately slow.** A test suite that hashes a password per test will crawl; use a
   low strength in the test profile only, and never let that setting reach production config.

---

## Definition of done for this turn

All twenty-four criteria in part 2 are true, `docs/audit/turn-1.md` records the five Merge-Readiness
criteria with evidence, and the branch merges to `main` with CI green.

---

## Accepted risk: registration discloses whether an email is registered

Criterion 1.3 requires `POST /api/auth/register` to answer 409 for an address that already has an
account. That makes the endpoint an account-enumeration oracle, and it contradicts `CLAUDE.md`'s
rule that an error must never reveal whether an email is registered — the same property
`AuthService.login` spends a dummy BCrypt verification to protect. The two rules disagree, and the
disagreement is recorded here rather than discovered later.

The standard fix is to answer 201 either way and tell the real owner by email. Notifications are on
the out-of-scope list for these three turns, so that path does not exist yet and a 409 that a user
can act on is better than a silent no-op. **The 409 stays, deliberately, and rate limiting (1.22)
bounds how fast a list can be walked.** The constant-time login work also stays: it costs nothing,
and it is the half that survives when notifications arrive and this endpoint stops answering.

---

## Revision log

| Date | Change |
|---|---|
| 2026-09-05 | First version, written before any implementation commit. |
| 2026-09-05 | Revised after `spec-test-writer` reported seven ambiguities it could not resolve without guessing. 1.7 names *which* family; 1.9 names the log level, since as written it was unsatisfiable by any implementation once the servlet container logs request bodies at TRACE; 1.11 says ignored rather than rejected; 1.12 puts response timing out of scope with a reason; slug normalisation is defined, having been named as a pitfall but never specified. Added 1.20 (the document never said how to authenticate) and 1.21 (credential records printed their password through the generated `toString()`). **These were defects in this document, not in the code** — which is the point of having tests written by someone who cannot see the implementation. |
| 2026-09-05 | Revised again after the security review. 1.7 extended to concurrent refreshes, because the sequential test passed while a race defeated reuse detection entirely. Added 1.22 (nothing limited the unauthenticated surface, and a failed login left no trace at any level), 1.23 (Spring MVC's own 4xx were being converted to 500 with a logged stack trace) and 1.24 (the API map was public). Registration's enumeration oracle recorded above as an accepted risk rather than left as an unnoticed contradiction between this document and `CLAUDE.md`. |
