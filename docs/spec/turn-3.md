# Turn 3 — Booking, Concurrency, the Public Page, and Deployment

> Module 10 specification. Written before the turn's first implementation commit. The turn-2 lesson
> is applied twice over: several criteria below name a *status* and a *body*, not just a behaviour,
> because every place a turn-2 criterion left that open became a defect in the specification.

---

## 1. Goal, and the reason for it

**Goal.** A customer opens a public page, sees the times a business is genuinely free, and books one
— without an account, and without anyone answering a message. The business sees it appear. Two
customers who click the same slot at the same instant produce exactly one appointment.

**Reason.** This closes the loop `docs/framing.md` opened. Turn 2 made availability computable;
until someone can take one of those slots, the problem statement is unaddressed — the owner is still
the booking system. The concurrency requirement is not a detail: the moment booking is public,
simultaneous requests stop being hypothetical, and a double booking is the one failure that costs a
business a customer *and* an apology, in person.

**How to resolve unanticipated forks.** Turn 2 said: refuse to offer a slot. This turn adds a
second, and where they conflict this one wins: **never record a booking the business cannot honour.**
Refusing a booking that would have been fine costs one customer one attempt; accepting one that
cannot be honoured costs a person standing in a shop that has no room for them.

---

## 2. Testable success criteria

### Booking, and the guarantee that makes it safe

| # | Criterion | Decided by |
|---|---|---|
| 3.1 | The database refuses two overlapping appointments for one employee, independently of application code — a direct `INSERT` of an overlapping row fails | `AppointmentConstraintIT.databaseRefusesAnOverlapWithoutApplicationCode` |
| 3.2 | Twenty concurrent identical booking requests produce **exactly one** appointment; every loser receives 409 with the code `SLOT_TAKEN` — one code, because a page that recognises only one of two will mishandle the other and show the silent failure 3.19 forbids | `AppointmentConcurrencyIT.exactlyOneOfManySimultaneousBookingsSucceeds` |
| 3.3 | A cancelled appointment does not block its former time: the slot reappears in availability and can be rebooked | `AppointmentLifecycleIT.cancellingFreesTheSlot` |
| 3.4 | Two appointments may touch — one ending exactly when the next begins is permitted | `AppointmentConstraintIT.backToBackAppointmentsArePermitted` |
| 3.5 | An appointment is refused with **409 `SLOT_NOT_AVAILABLE`** unless its start is a slot the availability engine actually offers. Distinct from `SLOT_TAKEN`: this time was never on offer, rather than offered and since taken | `BookingIT.refusesATimeThatWasNeverOffered` |
| 3.6 | An appointment is refused for an employee who does not perform the service | `BookingIT.refusesAnEmployeeWhoDoesNotPerformTheService` |
| 3.7 | Rescheduling into an occupied time leaves the original appointment exactly as it was | `AppointmentLifecycleIT.aFailedRescheduleChangesNothing` |
| 3.8 | Every status change writes one row to `appointment_status_history`, including creation | `AppointmentLifecycleIT.everyStatusChangeIsRecorded` |
| 3.9 | An appointment occupies `[start, end)` where `end = start + service duration` at the time of booking, so a later change to the service does not move existing appointments | `BookingIT.durationIsFixedAtBookingTime` |
| 3.10 | Booked time is busy: availability stops offering a slot once it is taken, through the same busy-interval input blocked times use | `AvailabilityIT.aBookedSlotIsNoLongerOffered` |

### The public surface

| # | Criterion | Decided by |
|---|---|---|
| 3.11 | A visitor with no account can read a business, its services, and availability, by slug | `PublicBookingIT.anonymousVisitorCanReachTheBookingSurface` |
| 3.12 | A visitor can create an appointment with no account | `PublicBookingIT.anonymousVisitorCanBook` |
| 3.13 | The public surface exposes no customer's name, email or phone, and no other appointment's details — only that a time is taken | `PublicBookingIT.publicSurfaceDisclosesNoCustomerData` |
| 3.14 | The public surface exposes no employee for a service they do not perform, and no internal id beyond what booking requires | `PublicBookingIT.publicSurfaceDisclosesNothingInternal` |
| 3.15 | Every public route is rate limited by address, more strictly than the authenticated API | `PublicRateLimitIT` |
| 3.16 | A public booking creates a customer for that business, or reuses the existing one with the same email, and never reaches a customer of another business | `PublicBookingIT.customerIsPerBusiness` |
| 3.17 | An unknown slug is refused identically to a slug that exists but is **not bookable** — defined as having no services, or nobody to perform them. Existing is not the same as being open, and only the second is public: otherwise the slug space is an enumeration oracle that reveals a business's name and time zone to anyone who guesses | `PublicBookingIT.unknownAndUnbookableSlugsAreIndistinguishable` |

### Interface

| # | Criterion | Decided by |
|---|---|---|
| 3.18 | A visitor can complete service → employee → date → slot → details → confirmation in the browser | `booking.spec.ts.aVisitorCanBookFromStartToFinish` |
| 3.19 | A slot taken between page load and submit shows a clear message and refreshed slots — never a silent failure, and never a confirmation | `booking.spec.ts.aSlotTakenWhileBookingIsReportedNotSwallowed` |
| 3.20 | The public page renders four distinguishable states. A business with nothing bookable is **not** one of them: 3.17 makes it indistinguishable from an address that does not exist, so the page shows the same not-found state for both | `booking.spec.ts` |
| 3.21 | The owner sees the appointment in the dashboard list, and can cancel it there | `dashboard.spec.ts.theOwnerSeesAndCancelsABooking` |
| 3.22 | The dashboard calendar shows a week, places each appointment in the right day and time in the **business's** zone, and shows an empty week as empty rather than blank | `dashboard.spec.ts.theCalendarPlacesAppointmentsCorrectly` |

### Deployment

| # | Criterion | Decided by |
|---|---|---|
| 3.23 | The application runs at a public HTTPS URL named in `README.md` | manual, recorded in the turn audit with the URL |
| 3.24 | Flyway migrates the deployed database on boot, from empty to current, with no manual step | the deployment log, quoted in the audit |
| 3.25 | No secret is present in any deployment configuration committed to the repository | `.githooks/pre-commit`, the CI secret scan, and inspection of the deploy config |
| 3.26 | A booking made against the deployed URL is visible in the deployed dashboard | manual, recorded in the audit |

### Controls the security review added

| # | Criterion | Decided by |
|---|---|---|
| 3.27 | An anonymous booking never changes contact details an existing customer already has; it may only fill in ones that are missing | `PublicBookingIT.anonymousBookingCannotRewriteAnExistingCustomer` |
| 3.28 | A booking is refused with 400 if it starts in the past, or beyond the configured horizon | `BookingIT.refusesAPastStart`, `.refusesBeyondTheHorizon` |
| 3.29 | The public surface names only people a visitor could book, and "bookable" requires somebody who performs a service **and** has working hours | `PublicBookingIT.publicSurfaceNamesOnlyBookablePeople`, `.aBusinessWithNobodyAbleToServeIsNotDiscoverable` |
| 3.30 | Behind a proxy, the rate limiter keys on the client's address rather than the proxy's, so one caller cannot exhaust everyone's allowance | reasoned in the audit against the deployed configuration; not decidable by a suite whose requests all originate from one address |
| 3.31 | The distinction between `SLOT_TAKEN` and `SLOT_NOT_AVAILABLE` reveals nothing about times the availability surface would not have offered anyway | `PublicBookingIT.theTwoConflictCodesRevealNoOccupancy` |
| 3.32 | Two visitors sharing an email address, booking different free times at once, both succeed — neither receives a server error | `AppointmentConcurrencyIT.twoBookingsSharingAnEmailBothSucceed` |

**Not claimed by this turn:** payments, notifications of any kind, customer accounts, recurring
appointments, and the mobile clients. All are on the out-of-scope list in `docs/framing.md` with
their reasons.

---

## 3. Architectural guidance

Boundaries only.

**The overlap guarantee lives in the database.** A `btree_gist` exclusion constraint on
`(employee_id, tstzrange(starts_at, ends_at, '[)'))`, restricted to statuses that occupy time, so a
cancelled appointment stops blocking without being deleted. Service code catches the violation and
answers 409. A lock or a check-then-insert in Java would depend on every future code path
remembering to take it; a constraint cannot be forgotten, and it holds for a repair script and a
second write path as well.

**Appointments become busy intervals.** The availability engine does not change: turn 2 built it not
to know what makes an interval busy, and this is the turn that collects on that. Appointments join
blocked times in the same list.

**The public surface is a separate controller with its own DTOs**, not the authenticated one with
the guard removed. Sharing a response shape between an owner's dashboard and an anonymous visitor is
how a customer's phone number ends up in a public JSON body — the shapes differ because the
audiences do, and the compiler should enforce that rather than a reviewer.

**Deployment:** Railway for the backend, PostgreSQL and Redis; Vercel for the frontend. Secrets as
environment variables set in the platform, never in the repository. `CORS_ALLOWED_ORIGINS` must name
the deployed frontend origin, or the deployed dashboard breaks exactly as it did locally.

---

## 4. Validation approach

- **Unit, no Spring context:** the booking domain rules that do not need a database.
- **Integration, Testcontainers:** everything above ending in `IT`, on real PostgreSQL, because the
  exclusion constraint is the guarantee and it does not exist in any substitute database.
- **Concurrency:** real threads against a real database. Criterion 3.2 uses a latch to release
  simultaneous requests and asserts exactly one success. A test that cannot fail under a correct
  implementation and cannot pass under a broken one is the only kind worth writing here.
- **Browser, Playwright, against the real API:** the public flow end to end, including the taken-slot
  path, which is only observable in a browser.
- **Written by `spec-test-writer` from this document**, with no access to `backend/src/main` or
  `frontend/src`. Criterion 3.2 especially: a concurrency test written by reading the implementation
  would test the lock the author had in mind rather than the guarantee the specification demands.
- **Gate:** CI green on all jobs before merge.

---

## 5. Known pitfalls

1. **`btree_gist` is an extension.** `CREATE EXTENSION IF NOT EXISTS btree_gist` must run in the
   migration, or the constraint cannot mix an equality column with a range column. It requires
   privileges some managed databases withhold — check the deployment target *before* relying on it,
   not on the evening of the deadline.
2. **A partial exclusion constraint needs its `WHERE` clause to match the statuses that occupy
   time.** Get it wrong in one direction and cancelled appointments block their old slot for ever;
   wrong in the other and a cancelled appointment can be double-booked into.
3. **The range must be half-open**, `'[)'`. With `'[]'` an appointment ending at 10:00 collides with
   one starting at 10:00, and back-to-back booking — the normal case for a barber — becomes
   impossible.
4. **A constraint violation arrives as `DataIntegrityViolationException`,** the same type as a
   duplicate key. Distinguish by constraint name, or a duplicate customer email will be reported as
   a double booking. Turn 2 shipped a 500 by not catching this class at all.
5. **Rescheduling is not delete-then-insert.** Done in two statements it can free the old slot,
   fail to take the new one, and leave the customer with nothing. One transaction, and the
   constraint decides.
6. **The public availability route must not simply reuse the authenticated one.** It needs its own
   rate limit, and it must not return whatever the dashboard returns.
7. **Do not trust the slot the browser sends.** It arrived from a page that may be minutes old.
   Re-derive availability server-side at booking time; the exclusion constraint is the last line,
   not the first.
8. **Timezones again:** an appointment is stored as instants, but a customer chose a wall-clock time
   in the business's zone. The confirmation must show the business's clock, not the visitor's.
9. **A deployed Flyway failure is invisible** unless someone reads the boot log. A container that
   restarts silently looks like a slow deploy.

---

## Definition of done for this turn

All thirty-two criteria in part 2 are true, `docs/audit/turn-3.md` records the five Merge-Readiness
criteria with evidence, the branch merges to `main` with CI green, and `README.md` names a URL a
reader can open.

---

## Revision log

| Date | Change |
|---|---|
| 2026-09-05 | First version, written before any implementation commit. |
| 2026-09-06 | Six criteria added after the security review, which found three HIGH defects in the project's first unauthenticated write. **3.27**: an anonymous booking rewrote an existing customer's name and phone, keyed only on an unverified email — and because the owner's list joins the customer by id, one booking would have replaced that person's real details against every appointment they had ever had. **3.28**: nothing bounded a booking in either direction, so an anonymous caller could write appointments into 2019 or 2099, and booking out every slot of every year was a matter of patience rather than of getting past a control. **3.30**: the limiter keyed on `getRemoteAddr()`, which behind a load balancer is the proxy — so "60 per address" was one global bucket, and a single shell loop would have taken the booking page down for everyone. **3.29** tightens "bookable", which counted rows rather than anyone able to perform anything, leaving half-configured businesses publicly discoverable. **3.31** closes an occupancy oracle in the pair of 409 codes. **3.32** exists because two visitors sharing an email address produced a 500. |
| 2026-09-06 | Revised after the verification suite found one defect and five ambiguities. 3.17 now defines "not bookable", because the implementation checked only that the slug existed and so answered 200 with a name and time zone for a business that had never opened — the same enumeration-oracle class as 1.5 and 1.12, and the natural thing to write. 3.20 follows from it: a business with nothing bookable is not a state the page can show, because it must not be distinguishable from an address that does not exist. 3.2 and 3.5 now name their codes: the losers of a race were split across `SLOT_TAKEN` and `SLOT_NOT_AVAILABLE` depending on whether the constraint or the re-derivation caught it, and a page recognising only one would have shown a silent failure — the booking service now distinguishes *never offered* from *offered and taken* rather than reporting whichever check fired first. **All four were defects in this document, not in the tests.** |
