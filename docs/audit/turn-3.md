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

### Interface (3.18–3.22) — **not verified**

No browser tests exist for these. See §1a.

### Deployment (3.23–3.26) — **not done**

The runbook is written ([`docs/deploy.md`](../deploy.md)) and nothing is deployed. See §1a.

### Controls the security review added (3.27–3.32) — met

| # | Criterion | Test |
|---|---|---|
| 3.27 | An anonymous booking cannot rewrite an existing customer's details | `PublicBookingIT` |
| 3.28 | A past or beyond-horizon start is refused | `BookingIT` |
| 3.29 | Only bookable people are named; "bookable" means able to serve | `PublicBookingIT` |
| 3.30 | The limiter keys on the client address behind a proxy | reasoned below; not decidable by the suite |
| 3.31 | The two conflict codes reveal no occupancy | `PublicBookingIT` |
| 3.32 | Two visitors sharing an email both succeed | `AppointmentConcurrencyIT` |

**3.30 is honestly weaker than the others and says so in the spec.** Every request in the suite
originates from 127.0.0.1, so "keyed per address" and "keyed globally" are indistinguishable to it.
What the fix rests on is `server.forward-headers-strategy`, set to `framework` in the deployment and
`none` by default — and the reason for that default is itself a control: enabling forwarded headers
without a trusted proxy in front lets a caller spoof `X-Forwarded-For` into an unlimited allowance.
Settling it requires the deployed instance, and §6 records the check to run there.

### 1a. What is not verified, stated plainly

- **3.18–3.22 (five criteria) have no automated cover.** The public booking flow, the taken-slot
  path, the four states, the owner's cancellation and the calendar's day placement are implemented
  and were exercised by hand (§6), but the browser suite for them is not written.
- **3.23–3.26 (four criteria) are not done.** Nothing is deployed, so there is no URL, no boot log
  showing Flyway migrating from empty, and no end-to-end pass against a public host.

Nine of thirty-two criteria therefore rest on manual verification or on nothing. That is recorded
here rather than reinterpreted, and it is why the verdict below is not a pass.

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
./mvnw -B clean verify     39 unit, 108 integration     BUILD SUCCESS
```

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

**Not ready to merge.** Twenty-three of thirty-two criteria are demonstrated by a named test or a
linked CI run; five (3.18–3.22) rest on the manual pass in §6; four (3.23–3.26) are not done.

By this pack's own rule that is not a pass, and softening it would defeat the purpose of having the
rule. What would make it one, in order: the browser suite for 3.18–3.22, the deployment in
`docs/deploy.md`, and the checks in §6 run against it.

The property this turn existed to establish — that two people cannot be sold the same slot — **is**
established, at the database level, and proven by a test that has already caught one real regression
in the code that surrounds it.
