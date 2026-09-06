# Bookly

Appointment booking for small service businesses — barbers, salons, trainers, tutors. A customer
opens a public page, sees the times an employee is *actually* free for the service they want, and
books one, without the owner answering a message to tell them.

Built for ASE-26 (Agentic Software Engineering) as the **own project**. The application is the
subject; the development method is the deliverable.

---

## How to read this repository

The grading rule is that only what can be opened and verified here counts. So the documents below
are the primary artefacts, and the code is downstream of them.

| Read this | For |
|---|---|
| [`docs/framing.md`](docs/framing.md) | The problem, the stakeholders, the testable definition of done, and what is deliberately out of scope — with reasons |
| [`docs/spec/`](docs/spec) | One specification per spiral turn, each committed **before** the code it generated |
| [`docs/audit/`](docs/audit) | One Merge-Readiness audit per turn: five criteria, each answered with a test name or a CI run rather than a claim |
| [`docs/coordination.md`](docs/coordination.md) | Which agents did what, and — more importantly — what each was and was not shown |
| [`CLAUDE.md`](CLAUDE.md) | The rules the agent works under |

**The turns are visible in the history.** Each is a branch merged into `main`:

```bash
git log --graph --oneline main
```

Within a turn, the order is always specification → implementation → verification → audit, and the
commit timestamps are the evidence that it happened in that order rather than being written up
afterwards.

---

## Running it

Requires Docker (or Colima), and JDK 21 plus Node 22 if you want to run the parts outside
containers.

```bash
cp .env.example .env
# JWT_SECRET has no default: the backend refuses to start without one, rather than
# quietly signing tokens with a key committed in this repository.
openssl rand -base64 48

docker compose up
```

- Frontend — http://localhost:3000
- API — http://localhost:8080
- OpenAPI — http://localhost:8080/swagger-ui.html
- Health — http://localhost:8080/actuator/health

### Backend on its own

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # the project targets Java 21
docker compose up -d postgres redis
cd backend && ./mvnw spring-boot:run
```

### Tests

```bash
cd backend
./mvnw test      # unit tests — no containers, fast enough for the pre-commit hook
./mvnw verify    # + integration, security and concurrency suites on real PostgreSQL and Redis
```

Testcontainers runs real PostgreSQL and Redis, never H2. A test against a different database than
production is evidence about the wrong system — and the exclusion constraint that prevents double
booking does not exist in H2 at all, so substituting it would quietly delete the guarantee the test
is there to prove.

```bash
cd frontend
npm ci && npm run lint && npx tsc --noEmit && npm run build
```

---

## Architecture

A **modular monolith**. One deployable, module boundaries drawn so the pieces stay separable — and
so the same REST API can serve the Android and iOS clients that come later without a browser
assumption anywhere in the contract.

```
Next.js (browser)  ─┐
Android (later)    ─┼─→  REST + OpenAPI  ─→  Spring Boot  ─→  PostgreSQL   (source of truth)
iOS (later)        ─┘                                     └─→  Redis        (cache, rate limits)
```

Three decisions worth knowing before reading the code:

**Tenant isolation is decided in one place.** Tenant-scoped routes are
`/api/businesses/{businessId}/...` and carry `@PreAuthorize("@tenantGuard.canAccess(#businessId)")`.
`TenantGuard` looks the caller up in `business_members` on every request. No `businessId` is ever
read from a request body. A business that does not exist and a business you are not a member of
return identical responses, because distinguishing them tells an attacker which ids are real.

**Double booking is prevented by the database, not by application code.** A Postgres `btree_gist`
exclusion constraint on `(employee_id, tstzrange(starts_at, ends_at))` refuses the overlapping row.
A lock in a service method depends on every future code path remembering to take it; a constraint
cannot be forgotten.

**Time is computed, never stored as slots.** All instants are `timestamptz`; each business carries
an IANA zone; availability is derived per request from working hours, service duration, existing
appointments and blocked time. A precomputed slot table would be a second source of truth that
drifts.

---

## Status

| Turn | Scope | State |
|---|---|---|
| 1 | Foundation, authentication, tenant isolation | complete — [audit](docs/audit/turn-1.md) |
| 2 | Services, employees, working hours, availability engine | complete — [audit](docs/audit/turn-2.md) |
| 3 | Booking, concurrency, public booking page, deployment | in progress |

Deployed URL: _pending — see [`docs/deploy.md`](docs/deploy.md) for the runbook._
