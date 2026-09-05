# Turn 2 — Business Configuration and the Availability Engine

> Module 10 specification. Written before the turn's first implementation commit. Five parts: goal
> *and reason*, testable success criteria, architectural guidance, validation approach, known
> pitfalls. Turn 1's lesson is applied here: several criteria below are more tightly worded than
> feels natural, because every place turn 1's spec was loose became a defect in the specification
> rather than in the code.

---

## 1. Goal, and the reason for it

**Goal.** A business can describe itself — its services, its employees, who performs what, when each
person works, and when they are unavailable — and the system can answer, for a given service, a
given employee (or any of them) and a given date, exactly which start times are genuinely free.

**Reason.** This is the problem statement made executable. `docs/framing.md` says the true
availability of an employee for a service is not written down anywhere: it is the intersection of
opening hours, that person's hours, the services they perform, how long the service takes, and what
is already blocked — and the owner recomputes it in their head, per enquiry, while working. Turn 2
is where that intersection stops living in someone's head. Everything the product claims rests on
this answer being right, because a wrong slot is not a cosmetic defect: it is a customer arriving to
a closed shop, or two people promised one chair.

The reason settles the forks this document does not anticipate. **When a rule is ambiguous, resolve
it toward refusing to offer a slot.** A slot wrongly withheld costs one booking; a slot wrongly
offered costs a customer's trust and the owner's morning.

---

## 2. Testable success criteria

Each resolves to one true/false answer and names the test that decides it.

### The engine

| # | Criterion | Decided by |
|---|---|---|
| 2.1 | A service of duration *d* is never offered a start time whose free window is shorter than *d* — the 45-minute service is not offered into a 30-minute gap | `AvailabilityCalculatorTest.doesNotOfferAServiceIntoAGapTooShortForIt` |
| 2.2 | No returned slot overlaps a busy interval; a slot may start exactly when one ends | `AvailabilityCalculatorTest.neverOverlapsABusyInterval`, `.mayStartExactlyWhenABusyIntervalEnds` |
| 2.3 | No returned slot falls outside the employee's working window for that date | `AvailabilityCalculatorTest.staysInsideTheWorkingWindow` |
| 2.4 | An employee with two working windows on one day (a break between them) yields slots in both and none in the gap | `AvailabilityCalculatorTest.respectsABreakBetweenTwoWindows` |
| 2.5 | Candidate start times are generated on the configured step from the start of each working window, in business-local wall-clock time | `AvailabilityCalculatorTest.generatesStartsOnTheConfiguredStep` |
| 2.6 | An employee not linked to the requested service contributes no slots | `AvailabilityIT.unlinkedEmployeeContributesNothing` |
| 2.7 | With no employee requested, the result is the union of every eligible employee's availability, deduplicated by start instant, and each slot names which employees can serve it | `AvailabilityCalculatorTest.anyEmployeeUnionsAndDeduplicates` |
| 2.8 | A day on which the employee has no working window returns an empty list, not an error | `AvailabilityCalculatorTest.aNonWorkingDayReturnsNoSlots` |
| 2.9 | Availability is computed from the inputs on every request: no table stores generated slots | `SchemaConventionsIT.noPreGeneratedSlotTable` |

### Time, which is where this will actually break

| # | Criterion | Decided by |
|---|---|---|
| 2.10 | On the spring-forward date, a working window spanning the transition yields exactly one hour fewer slots than the same window on an ordinary day, and every returned instant is a real instant | `AvailabilityCalculatorTest.springForwardLosesExactlyTheSkippedHour` |
| 2.11 | On the fall-back date, the same window yields exactly one hour more, and no two returned slots are the same instant | `AvailabilityCalculatorTest.fallBackGainsAnHourWithoutDuplicateInstants` |
| 2.12 | A working window whose local start time does not exist on the spring-forward date does not produce a slot at a non-existent local time | `AvailabilityCalculatorTest.doesNotOfferANonExistentLocalTime` |
| 2.13 | The same request answered for two businesses in different time zones returns different instants for the same local hours | `AvailabilityIT.zoneIsTakenFromTheBusiness` |

### Configuration surface

| # | Criterion | Decided by |
|---|---|---|
| 2.14 | Services, employees, employee↔service links, working hours and blocked times can each be created, listed and deleted through the API | `BusinessConfigurationIT` |
| 2.15 | A service duration must be positive and a whole number of minutes; a zero or negative duration is refused with 400 | `BusinessConfigurationIT.refusesNonPositiveDuration` |
| 2.16 | A working window whose end is not after its start is refused with 400 | `BusinessConfigurationIT.refusesInvertedWorkingWindow` |
| 2.17 | Every new route added in this turn is tenant-scoped and refuses a caller who is not a member | `TenantIsolationIT` — generated from the route table, so this criterion cannot be met by forgetting a route |
| 2.18 | An employee, service, working window or blocked time belonging to another business cannot be read, modified or deleted, even by id | `TenantIsolationIT.crossTenantResourceIsRefused` |
| 2.19 | Deleting a service removes its employee links and does not orphan rows | `BusinessConfigurationIT.deletingAServiceRemovesItsLinks` |

### Interface

| # | Criterion | Decided by |
|---|---|---|
| 2.20 | Each dashboard screen renders four distinguishable states — loading, empty, error, content | `dashboard.spec.ts` |
| 2.21 | A business with no services and no employees is told what to do next, not shown an empty table | `dashboard.spec.ts.newBusinessIsGuided` |
| 2.22 | The availability view shows, for a chosen service and date, the real slots from the engine, and says so plainly when there are none | `dashboard.spec.ts.availabilityShowsRealSlotsOrSaysThereAreNone` |

**Not claimed by this turn:** appointments, booking, cancellation, the public booking page, and the
overlap constraint. Those are turn 3. Buffers between appointments, per-service pricing beyond a
stored amount, and recurring blocked times are on the out-of-scope list.

---

## 3. Architectural guidance

Boundaries only; the interior is the implementer's.

`AvailabilityCalculator` is a **pure function**: working windows, busy intervals, service duration,
step, zone and date in — slots out. No repository, no clock lookup, no Spring context, so the logic
that matters can be tested exhaustively without a database.

**It must not know what makes an interval busy.** It receives busy intervals; in this turn they come
from blocked times alone, and in turn 3 appointments join the same list without the calculator
changing. A calculator that queries appointments directly would have to be reopened for every future
source of unavailability.

Working hours are stored per employee as (weekday, local start, local end), and a break is
represented as **two windows on the same day** rather than as a separate breaks table — the
intersection logic then handles breaks, split shifts and part days without a second concept. Blocked
times are absolute instant ranges, and one with no employee applies to the whole business, which is
how a public holiday is expressed.

Availability in this turn lives at `/api/businesses/{businessId}/availability` and is
**authenticated**, like every other route here. The unauthenticated equivalent that the public
booking page needs is turn 3's, and it will be a separate route with its own rate limit, because a
public endpoint has different exposure from an owner's dashboard.

---

## 4. Validation approach

Named before building.

- **Unit, no Spring context, over the pure calculator** — this is where the engine is actually
  verified. Every criterion from 2.1 to 2.12 is decidable without a database, and if any of them
  needs one, the calculator has taken a dependency it should not have.
- **Integration, Testcontainers on real PostgreSQL and Redis** — the query paths, the configuration
  endpoints, tenant isolation, and the zone-per-business behaviour.
- **Browser, Playwright** — the four states and the two guidance criteria, against the standalone
  build.
- **The engine's tests are written by `spec-test-writer` from this document alone**, with no access
  to `backend/src/main`. This matters more here than it did in turn 1: a test written by reading the
  calculator would re-state its arithmetic, and arithmetic that agrees with itself is exactly the
  failure mode. The DST criteria in particular must be derived from what the clock does, not from
  what the code does.
- **Gate:** CI green before merge, and criterion 2.17 is satisfied by the route table rather than by
  a hand-maintained list, so a route added without isolation coverage fails the build.

---

## 5. Known pitfalls

The warnings that would be given to a colleague starting this turn.

1. **Adding a duration to an instant is not the same as adding it in a zone.** `instant.plus(30,
   MINUTES)` crosses a DST boundary as though it were not there. Slot arithmetic happens in
   `ZonedDateTime`, and only the final answer is converted to an instant.
2. **`LocalTime` 02:30 does not exist on the spring-forward date.** `ZonedDateTime.of` will silently
   move it rather than fail, which is the right behaviour for a clock and the wrong one for a
   promise to a customer. Any window boundary that lands in the gap must be handled deliberately,
   and criterion 2.12 exists because the default is quiet.
3. **On the fall-back date one local time occurs twice.** `ZonedDateTime.of` picks the earlier
   offset. Generating slots by local time alone will silently drop an hour of real availability;
   generating them by instant will produce two slots that print the same local time. Criterion 2.11
   is written against instants for this reason.
4. **The step is not the duration.** A 45-minute service on a 15-minute step starts at :00, :15,
   :30, :45 — not only on 45-minute boundaries. Conflating the two produces a calendar that looks
   plausible and quietly loses most of the day's capacity.
5. **Half-open intervals, consistently.** A slot occupies `[start, end)`. A slot may begin exactly
   when a busy interval ends. Getting this wrong produces an off-by-one that shows up as a mysterious
   missing slot at the top of every hour, and 2.2 pins both directions.
6. **`business_members` is not the same as `employees`.** A member is a login; an employee is someone
   with working hours who performs services. They will be tempting to merge, and merging them means
   every employee needs an account and no employee can be added before they accept an invitation.
7. **The tenant guard covers the business, not the row.** `GET /businesses/{a}/employees/{x}` passes
   the guard when the caller belongs to business `a` even if employee `x` belongs to business `b`.
   Every lookup must filter on both. Criterion 2.18 exists because the guard alone reads as
   sufficient and is not.
8. **The route-table isolation test will fail loudly the moment a new route appears**, which is the
   point. It is not a broken test; it is the guardrail asking for a case.
9. **Do not let the dashboard drive the API shape.** The same endpoints serve Android and iOS later.
   A response shaped for one screen is one that gets rewritten twice.

---

## Definition of done for this turn

All twenty-two criteria in part 2 are true, `docs/audit/turn-2.md` records the five Merge-Readiness
criteria with evidence, and the branch merges to `main` with CI green.

---

## Revision log

| Date | Change |
|---|---|
| 2026-09-05 | First version, written before any implementation commit. |
