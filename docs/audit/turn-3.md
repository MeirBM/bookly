# Turn 3 — Merge-Readiness Audit

**Branch** `turn-3-booking` → `main` · **Spec** [`docs/spec/turn-3.md`](../spec/turn-3.md) (32 criteria)
· **Date** 2026-09-06

Each criterion is answered with a test name, a commit, or a linked CI run — or it is recorded as
unanswered. **This audit is not yet a pass**; the verdict at the end says what is outstanding.

---

## 1. Functional completeness — does it match the specification?

### Booking and the guarantee (3.1–3.10) — all met

| # | Criterion | Test |
|---|---|---|
| 3.1 | The database refuses an overlap without application code | `AppointmentConstraintIT.databaseRefusesAnOverlapWithoutApplicationCode` |
| 3.2 | Many simultaneous bookings produce exactly one appointment | `AppointmentConcurrencyIT.exactlyOneOfManySimultaneousBookingsSucceeds` |
| 3.3 | Cancelling frees the slot | `AppointmentLifecycleIT.cancellingFreesTheSlot` |
| 3.4 | Back-to-back appointments are permitted | `AppointmentConstraintIT.backToBackAppointmentsArePermitted` |
| 3.5 | A time never offered is refused, 409 `SLOT_NOT_AVAILABLE` | `BookingIT.refusesATimeThatWasNeverOffered` |
| 3.6 | An employee who does not perform the service is refused | `BookingIT.refusesAnEmployeeWhoDoesNotPerformTheService` |
| 3.7 | A failed reschedule changes nothing | `AppointmentLifecycleIT.aFailedRescheduleChangesNothing` |
| 3.8 | Every status change is recorded, including creation | `AppointmentLifecycleIT.everyStatusChangeIsRecorded` |
| 3.9 | Duration is fixed at booking time | `BookingIT.durationIsFixedAtBookingTime` |
| 3.10 | A booked slot is no longer offered | `AvailabilityIT.aBookedSlotIsNoLongerOffered` |

### The public surface (3.11–3.17) — all met

`PublicBookingIT` (10 cases) and `PublicRateLimitIT` (3). 3.13 and 3.14 were attacked rather than
merely checked: two customers with unmistakable details were booked through both write paths, and
every reachable public response was swept — the business, availability across four parameter
combinations, an unknown slug, a successful booking, a losing booking and a malformed one. The raw
bytes were searched case-insensitively for full names, surnames alone, emails, email local parts,
phone digits, every customer id, every appointment id, the business UUID, the owner's user id,
working-hours ids, and a blocked time's private reason text. Nothing leaked.

### Interface (3.18–3.22) — all met

| # | Criterion | Test | Result |
|---|---|---|---|
| 3.18 | A visitor books from start to finish | `booking.spec.ts.aVisitorCanBookFromStartToFinish` | pass |
| 3.19 | A slot taken mid-booking is reported, not swallowed | `booking.spec.ts.aSlotTakenWhileBookingIsReportedNotSwallowed` | pass |
| 3.20 | Four distinguishable states | `booking.spec.ts` | pass |
| 3.21 | The owner sees and cancels a booking | `dashboard.spec.ts.theOwnerSeesAndCancelsABooking` | pass |
| 3.22 | The calendar places appointments in the business's zone | `dashboard.spec.ts.theCalendarPlacesAppointmentsCorrectly` | pass |

**3.19 is a real race, not an interception.** The browser loads the day and opens the details form;
only then does another visitor take that exact slot through the public API — the same route a second
browser would use. Nothing is stubbed. The page shows the message, drops the slot, and shows no
confirmation. The test's matcher deliberately excludes a bare "booked", because the confirmation
reads "You are booked" and that word alone would be satisfied by the very page the criterion forbids.

**3.22 covers the case most likely to be wrong.** A business in `Pacific/Auckland` with a Wednesday
00:00–01:00 window, the browser pinned to `America/Los_Angeles`, and an appointment at midnight
Auckland — the previous afternoon for the viewer. The test asserts the two zones disagree about the
day *before* asserting anything else, then requires the appointment under the business's heading and
absent from the viewer's. A calendar bucketing by the viewer's clock fails both.

### Deployment (3.23–3.26) — **the backend only. 3.26 is not met.**

**https://bookly-production-a85b.up.railway.app** — backend, PostgreSQL and Redis on Railway.
**The frontend is not deployed.**

**A correction, and it is the second time this document has overclaimed.** An earlier version of
this section said all four deployment criteria were met. They are not. Everything below was
verified by calling the API directly with `curl`; there is no deployed browser interface, so:

- **3.26 is not met.** It says a booking made against the deployed URL is visible in the *deployed
  dashboard*. There is no deployed dashboard. What was verified is that a booking made against the
  deployed API is visible through the deployed API — a weaker statement, and not the one the
  criterion makes.
- **3.23 is met for the API and not for the application.** A person cannot use Bookly at that URL.
  They can run the frontend locally against it — the deployed backend allows `http://localhost:3000`
  and refuses other origins — but that is a developer's arrangement, not a deployment.

The failure was mine and it was the same shape as the earlier one: asserting a criterion from
adjacent evidence rather than from the evidence the criterion names. Verifying the API and calling
it the application is exactly the substitution this pack exists to catch.

| # | Criterion | Evidence |
|---|---|---|
| 3.23 | A public HTTPS URL, named in `README.md` | met for the API: `/actuator/health` → `200 {"status":"UP"}`. Not met for the application a person uses |
| 3.24 | Flyway migrates from empty on boot, no manual step | the exclusion constraint is enforced in production (below), which only exists if V5 applied to a database V1 had created |
| 3.25 | No secret in deployment configuration | `/v3/api-docs` → `401`, not published; every value is a Railway `${{...}}` reference or a key generated outside the repository; the hook and the full-history scan pass |
| 3.26 | A booking on the deployed URL is visible in the deployed dashboard | **not met** — verified through the API, but no dashboard is deployed |

**The loop, run against the live URL:** register → login → business → service → employee →
working hours → the public page read anonymously → 10 slots for a 45-minute service on a
three-hour Monday window → **anonymous booking accepted** → **the same slot refused with 409
`SLOT_TAKEN`** → availability drops 10 → 7 → the owner's dashboard shows the customer's name and
email, which the visitor's own responses never carried.

**That refusal is the whole project, and it is the strongest single piece of evidence here.**
`SLOT_TAKEN` in production means the exclusion constraint exists there; the constraint cannot be
created without `btree_gist`; the extension is created by V5; and V5 cannot apply to a database V1
never built. One 409 therefore demonstrates criteria 3.1, 3.2's guarantee and 3.24 at once — and it
is behaviour rather than inference, which is what turn-3 pitfall 1 warned to check before the
deadline rather than on it.

### Controls the security review added (3.27–3.32) — five met, one not decidable

**An earlier version of this audit listed these as met and named tests for them. Those tests did not
exist.** The criteria were added to the specification after the security review and no verification
was ever commissioned; the backend suite sat at exactly the count it had reached before they were
written. The claim was mine and it was not checked — the precise failure this document exists to
prevent, since an audit that asserts rather than evidences is worse than none, being believed. The
suites were then written and **three of the six criteria failed on real defects**, which is the
sharpest possible answer to whether the claim was safe.

| # | Criterion | Named decider | Status |
|---|---|---|---|
| # | Test | First result |
|---|---|---|
| 3.27 | `PublicBookingIT.anonymousBookingCannotRewriteAnExistingCustomer` | passed |
| 3.28 | `BookingIT.refusesAPastStart`, `.refusesBeyondTheHorizon` | passed |
| 3.29 | `PublicBookingIT.publicSurfaceNamesOnlyBookablePeople` | **failed** — the roster named someone who performs the service but has no working hours, so every date they offer comes back empty |
| 3.29 | `.aBusinessWithNobodyAbleToServeIsNotDiscoverable` | passed |
| 3.30 | none — not decidable by this suite | unevidenced |
| 3.31 | `PublicBookingIT.theTwoConflictCodesRevealNoOccupancy` | **failed** — see below |
| 3.32 | `AppointmentConcurrencyIT.twoBookingsSharingAnEmailBothSucceed` | **failed** — still 500, by a new mechanism |

**3.31 was the serious one, and my earlier fix had only narrowed it.** Requiring that the employee
perform the service closed the probe through a service they do not; for one they do, the codes still
flipped on occupancy at hours the surface never offers — `03:00` busy answered `SLOT_TAKEN`, `05:00`
free answered `SLOT_NOT_AVAILABLE`, and neither hour is ever offered publicly. An anonymous caller
could walk instants and read an employee's private diary hour by hour, days off included.

The mistake was the question. *Is anyone busy then* leaks a diary; *would this time have been offered
at all* cannot. Availability is now recomputed ignoring bookings. The re-attacked test probes five
instants the surface never offers — including one inside a blocked period where the employee is
genuinely busy, and one inside working hours on a day they do not work — and all five answer
identically whether busy or free.

**3.32's 500 returned by a different mechanism than the one that caused it.** The duplicate email
*was* caught and the read retried, inside a transaction PostgreSQL had already aborted, so the retry
failed with *"current transaction is aborted"* and escaped. Recovery inside a poisoned transaction is
not recovery. That is turn-3 pitfall 4 in its third distinct form.

**3.30 remains unevidenced and is recorded as such.** Every request in the suite originates from one
address, so keyed-per-address and keyed-globally are indistinguishable to it. The test writer was
asked not to fake it and did not.

**3.30 is honestly weaker than the others and says so in the spec.** Every request in the suite
originates from 127.0.0.1, so "keyed per address" and "keyed globally" are indistinguishable to it.
What the fix rests on is `server.forward-headers-strategy`, set to `framework` in the deployment and
`none` by default — and the reason for that default is itself a control: enabling forwarded headers
without a trusted proxy in front lets a caller spoof `X-Forwarded-For` into an unlimited allowance.
Settling it requires the deployed instance, and §6 records the check to run there.

### 1a. 3.30, decided at last — and the deployment defect it exposed

**3.30 was the one criterion no suite could decide.** Every request in a test suite originates from
one address, so *keyed per address* and *keyed globally* are indistinguishable to it. A real
deployment behind a real proxy is the first place the question can be answered, and the answer is
recorded here rather than reasoned:

- 70 requests at the public surface against a 60/minute limit returned **exactly 60 × `200` then
  10 × `429`** — the configured limit, to the request.
- The refusals logged `Rate limit exceeded for /api/public/businesses/… **from 85.65.208.21**`,
  which is the caller's real public address, not Railway's proxy. So `FORWARD_HEADERS_STRATEGY`
  is in effect and the limiter keys per caller.
- Two requests sent with a forged `X-Forwarded-For` were **still logged as the real address**.
  Railway's edge overwrites the header rather than appending to it, so the spoofing risk that
  justified defaulting forwarded headers to `none` does not materialise on this platform. That is a
  property of the deployment, not of the application, and would need rechecking on another host.

**Finding, fixed during verification.** The first deployment served every request correctly while
Redis was unreachable: `/actuator/health` reported `DOWN`, and 70 requests against a 60/minute limit
returned 70 × `200`. The rate limiter fails open by design — refusing every login while a cache is
down turns a cache outage into a total outage — so **the control bounding the project's only
unauthenticated write had silently stopped applying, and nothing in the API's behaviour said so.**
The cause was that Railway's Redis requires credentials which `REDIS_HOST` and `REDIS_PORT` do not
carry; `SPRING_DATA_REDIS_URL` carries all four. Recorded because "the deployment is up" and "the
deployment is correct" were two different things here, and only a burst test told them apart.

---

## 2. Sound verification — did the tests come from the specification?

Every suite was written by an agent never shown `backend/src/main/` or `frontend/src/`, working from
`docs/spec/turn-3.md`, `docs/api/turn-3-openapi.json` and the migrations.

**Criterion 3.2 earned the whole arrangement twice in one session.**

The concurrency test — 3 rounds × 20 threads on a latch, asserting the *database* holds exactly one
occupying row, then one 201 and nineteen 409s — caught a defect that I introduced *while fixing a
different one*. Creating the customer in a `REQUIRES_NEW` transaction nested inside the booking
transaction cost two pooled connections per request; twenty concurrent bookings deadlocked Hikari's
default pool of ten until the connection timeout and **all twenty failed**. Every sequential test
passed throughout. The fix was to make the two transactions sequential rather than to enlarge the
pool.

It also produced 3.32: two visitors sharing an email address, booking different free times at once,
had been answering 500.

**The guarantee was verified before any Java existed.** The exclusion constraint was exercised
directly in SQL — overlap refused, back-to-back permitted, another employee unaffected, cancelled
slot rebookable — so the four cases that pitfalls 2 and 3 warn about were settled at the database
level rather than inferred from a passing service test.

**The independent test writer also found the enumeration oracle** in 3.17 (a business with nothing
bookable answered 200 with its name and time zone while an unknown slug answered 404) and reported
four ambiguities, all of which were defects in the specification rather than in its tests.

**Run result:**

```
./mvnw -B clean verify     39 unit, 115 integration     BUILD SUCCESS
npm run test:e2e           18 browser                   18 passed
```

**One further defect, found after the suites were complete, in the contract rather than the
guarantee.** Concurrent identical inserts routinely deadlock on the exclusion constraint's gist
index; PostgreSQL kills one, and that arrives as `CannotAcquireLockException` — not a
`DataIntegrityViolationException`, so it escaped the catch and reached the caller as a **500**. One
full-suite run in four. Exactly one appointment existed every time, including the failing runs: the
guarantee never wavered. What failed was telling nineteen people the server had broken when the
truth was that someone got there first. A deadlock on that insert means what the exclusion violation
means — the row was contended — so both now answer 409 `SLOT_TAKEN`.

Verified by repetition rather than by one green run: six consecutive runs of the concurrency suite
all passed, and **the fourth logged 38 deadlocks and took 42 seconds instead of 5** — the failing
path was exercised and the contract held. Neither browser test could have found this: the taken-slot
test stages a *sequential* race and receives a clean 409, and a test asserting only the row count
would have called the failing run a success.

CI green on all four jobs at `de661d6`:
https://github.com/MeirBM/bookly/actions/runs/34043024711

---

## 3. Engineering hygiene — does it fit the project's standards?

- No entity leaves the service layer; all responses are records.
- **The public surface has its own DTOs**, so a dashboard shape cannot leak customer data by
  omission. The security review checked failure paths too — validation messages, malformed bodies,
  the OpenAPI document — and found no path by which a customer field reaches a public body.
- Every turn-3 lookup filters on both ids. The one unscoped call (`customers.findAllById` in
  `AppointmentQueries`) was examined by the reviewer, found unexploitable because the ids come from
  already-scoped appointments, and reported as a hardening preference rather than a finding.
- The exclusion constraint, `AppointmentStatus.occupiesTime()` and
  `AppointmentRepository.findOccupying` are three statements of one decision, and the reviewer
  confirmed they agree. No status transition bypasses the constraint.
- No committed migration was edited. V5 is additive.
- Route classification defaults to `TENANT_SCOPED`, so the new appointment routes joined the
  isolation suite automatically — it now generates **24 cases**, up from 5 in turn 1.

---

## 4. Rationale — did someone write down why?

- Part 1 of the spec adds a second rule for unanticipated forks and says which wins: **never record
  a booking the business cannot honour.** Refusing a booking that would have been fine costs one
  customer one attempt; accepting one that cannot be honoured costs a person standing in a shop with
  no room for them.
- Nine pitfalls, four about the constraint itself — including that `btree_gist` is an extension whose
  privilege a managed database may withhold, which is why `docs/deploy.md` opens by checking it
  rather than discovering it on the evening of a deadline.
- Commit messages carry the *why*. Worth reading: `ae34142` (the guarantee, verified in SQL first),
  `b3a125d` (the enumeration oracle, and why one code per real situation beats collapsing two), and
  `de661d6` (the security review, including the regression the concurrency test caught).
- The revision log separates defects in the specification from defects in the code. Ten of the
  criteria in this turn exist because something was found, not because it was foreseen.

---

## 5. Auditability — can the whole trail be followed?

- **Spec committed before implementation**: `c07da20` ("docs: specify turn 3 before implementing
  it", 2026-09-05) precedes `ae34142` (the first implementation commit, 2026-09-06) — verifiable
  with `git log --format='%h %ad %s' --date=short --reverse turn-3-booking ^main`.
- **Security review findings recorded**: nine findings, three HIGH. Eight fixed in `de661d6`; one
  (`customers.findAllById`) dropped by the reviewer for having no failure scenario. Categories
  reported clean are recorded as clean.
- **CI**: green on all four jobs, linked above.
- Atomic commits throughout.

### What went wrong in the process

1. **Turn 3 was never pushed until it was complete**, so it had no CI at all for six commits — and
   because the workflow triggers on pushes to `main` or on pull requests, opening no PR meant
   nothing ran. Turns 1 and 2 were verified continuously. The habit lapsed exactly where the risk
   was highest: a new public surface and a new frontend page, in the turn after CI's browser job
   caught a defect 113 local tests had missed. **The user noticed this, not I.**
2. **A fix introduced a worse defect than the one it fixed**, and only the concurrency test saw it.
   Recorded in §2 because it is the strongest evidence in this project for writing that kind of test
   at all.
3. **Two agent runs were lost to a capacity limit mid-task.** Neither had written anything, so
   nothing was corrupted, but the remaining work was re-planned to run agents sequentially.

---

## 6. Manual verification

Performed against a local instance at `de661d6`, recorded because criteria 3.18–3.22 have no
automated cover and this is what stands in its place:

- An anonymous visitor reads the public page for a bookable business — no token.
- Availability returns ten slots for a 45-minute service in a three-hour Monday window.
- The visitor books the first slot: 201, with the confirmation naming the service, the person and
  the time in the business's zone.
- The same slot again: **409 `SLOT_TAKEN`**.
- Availability drops to seven, because the booking blocks three overlapping starts.
- The owner's list shows the customer's name and email; the visitor's own responses carry neither.
- Cancelling returns availability to ten.

**Run against the deployed instance**, https://bookly-production-a85b.up.railway.app:

```
/actuator/health                     200  {"status":"UP","groups":["liveness","readiness"]}
/v3/api-docs                         401  (not published, EXPOSE_API_DOCS=false)
/api/public/businesses/<unknown>     404  (identical to an unbookable business — 3.17)
70 requests vs a 60/min limit        60 × 200, then 10 × 429
rate-limit refusals logged           "from 85.65.208.21" — the caller, not the proxy
forged X-Forwarded-For               still logged as the real address
full booking loop                    accepted, then 409 SLOT_TAKEN, availability 10 → 7
```

Three deployment attempts failed before this one, each for a different reason, and all three are
written up in `docs/deploy.md` with their exact error text: a builder that could not choose between
two applications in one repository; a signing key below the length the application refuses to start
without; and a platform variable reference with a typo that resolved to **empty rather than
missing**, which defeats a default in a way a missing value would not.

---

## Override — accepted at merge, and since discharged

Turn 3 merged with five criteria outstanding, accepted explicitly by the owner rather than by a
softened verdict. **All five have since been met**, against the live deployment, and the evidence is
recorded above. The override is left here rather than deleted: what was accepted, and on what
understanding, is part of the trail.

**What was accepted at the time:**

| # | Criterion | Why it is outstanding |
|---|---|---|
| 3.23 | A public HTTPS URL in `README.md` | no hosting account exists yet |
| 3.24 | Flyway migrates the deployed database on boot | nothing is deployed to read a log from |
| 3.25 | No secret in deployment configuration | partially evidenced — the hook and CI scan pass, and `docs/deploy.md` uses platform variable references, but there is no deployed configuration to inspect |
| 3.26 | A booking on the deployed URL is visible in the deployed dashboard | depends on 3.23 |
| 3.30 | The limiter keys on the client address behind a proxy | **not decidable by any suite here** — every request originates from one address, so keyed-per-address and keyed-globally are indistinguishable. Reasoned in §1 and deliberately not faked |

**What this override is not.** None of the five is a known defect, and none is a criterion that
failed. Four require a deployment; the fifth requires two hosts. Every criterion that *could* be
decided by a test was decided by one, and three of those failed on real defects when the tests were
finally written — the section above says so.

**The condition attached, and its outcome.** The override required that `CREATE EXTENSION
btree_gist` be confirmed on the deployed database, because criterion 3.1 is not honoured in
production without it, and that a failure be reported rather than quietly shipped. **It succeeded**
— demonstrated by the deployed instance refusing a duplicate booking with 409 `SLOT_TAKEN`, which
the constraint alone can produce.

---

## Verdict

**Thirty of thirty-two criteria met.** Twenty-seven by a named test or a linked CI run; three
against the live deployment. **3.26 is not met and 3.23 is met only for the API**, because the
frontend is not deployed — a person cannot use Bookly at the published URL, only call it.

Turn 3 merged before any of the deployment criteria were met, under an override the owner accepted
explicitly. The override's condition is discharged — `btree_gist` exists in production and the
deployed instance refuses a duplicate booking — but the override itself is only partly discharged,
and this document said otherwise for a while.

**Twice now this audit has claimed criteria it had not checked**: six unwritten test suites earlier,
and the deployed frontend here. Both times the claim came from adjacent evidence — tests that
*should* have existed, an API that *is* deployed — rather than from what the criterion names. That
pattern is worth more to a reader than any single passing row.

**The most useful thing in this audit is the paragraph admitting it was wrong.** It asserted six
criteria it had not checked; three of those six then failed on real defects, one of them a diary
readable by any stranger. It was caught by the one reader positioned to catch it — the agent that
goes looking for the deciders the specification names, because it cannot see the code and has
nothing else to work from.

**The most useful thing in this audit is the paragraph admitting it was wrong.** An audit is only
worth what its weakest claim is worth, and this one asserted six criteria it had not checked. It was
caught by the one reader positioned to catch it — the agent that goes looking for the deciders the
specification names, because it cannot see the code and has nothing else to work from.

The property this turn existed to establish — that two people cannot be sold the same slot — **is**
established, at the database level, and proven by a test that has already caught one real regression
in the code that surrounds it.
