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
| 1.7 | A refresh token reused after rotation invalidates the whole token family for that user | `RefreshRotationIT.reuseRevokesFamily` |
| 1.8 | An expired access token is rejected with 401 on every authenticated route | `AuthFlowIT.expiredAccessTokenRejected` |
| 1.9 | Stored passwords are BCrypt hashes; the plaintext appears in no row and in no log line | `PasswordStorageIT.passwordIsNeverStoredOrLoggedInPlaintext` |
| 1.10 | A user authenticated against Business A receives 403 on every tenant-scoped route of Business B | `TenantIsolationIT` — one parameterised case per registered route |
| 1.11 | A `businessId` supplied in a request **body** never affects which business is read or written | `TenantIsolationIT.bodyBusinessIdIsIgnored` |
| 1.12 | A request for a business that does not exist and a request for a business the caller is not a member of return responses that cannot be distinguished | `TenantIsolationIT.absentAndForbiddenAreIndistinguishable` |
| 1.13 | `POST /api/businesses` creates a business, makes the creator its `BUSINESS_OWNER`, and assigns a unique slug | `BusinessCreationIT.creatorBecomesOwner` |
| 1.14 | Two businesses cannot hold the same slug | `BusinessCreationIT.slugIsUnique` |
| 1.15 | Every timestamp column added in this turn is `timestamptz` | `SchemaConventionsIT.allTimestampsAreTimestamptz` — reads `information_schema` |
| 1.16 | The OpenAPI document at `/v3/api-docs` lists every route added in this turn with its response codes | `OpenApiIT.documentsEveryRoute` |
| 1.17 | The frontend redirects an unauthenticated visit to `/dashboard` to `/login` | `dashboard.spec.ts` |
| 1.18 | CI runs the full suite on the pull request and is green | the linked run in `docs/audit/turn-1.md` |
| 1.19 | The secret scan runs over the full history, not only the tip, and finds nothing | the same CI run |

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

All nineteen criteria in part 2 are true, `docs/audit/turn-1.md` records the five Merge-Readiness
criteria with evidence, and the branch merges to `main` with CI green.
