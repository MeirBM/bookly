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

### Deployment (3.23–3.26) — **not done**

The runbook is written ([`docs/deploy.md`](../deploy.md)) and nothing is deployed. See §1a.

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

### 1a. What is not met, stated plainly

- **3.23–3.26 (four criteria) are not done.** Nothing is deployed: no URL, no boot log showing
  Flyway migrating from empty, no end-to-end pass against a public host. The runbook is written and
  the deployment needs an account this project does not have.
- **3.30 is unevidenced** — reasoned, not tested, for the reason given above.

Five of thirty-two criteria are therefore unmet or unevidenced. Everything else is demonstrated by a
named test or a linked CI run.

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

**Still to run on the deployed instance** (criteria 3.23–3.26, and settling 3.30):

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- must succeed, or 3.1 is not honoured in production
```
```bash
curl -fsS https://<backend>/actuator/health
curl -fsS https://<backend>/v3/api-docs      # must be 403
# then: 61 rapid public requests from one host must NOT return 429 to a second host
```

---

## Verdict

**Not ready to merge — for one reason, and it is not a defect.** Twenty-seven of thirty-two criteria
are demonstrated by a named test or a linked CI run. Four (3.23–3.26) require a deployment this
project has no account for. One (3.30) is reasoned rather than tested, because no suite whose
requests share an address can decide it.

By this pack's rule that is not a pass, and softening it would defeat the purpose of the rule. What
would make it one: the deployment in `docs/deploy.md`, and the checks in §6 run against it.

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
