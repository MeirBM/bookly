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
| 2.7 | With no employee requested, the result is the union of every eligible employee's availability, deduplicated by start instant, and each slot names which employees can serve it | Two deciders, because one cannot see both halves: `AvailabilityCalculatorTest.anyEmployeeUnionsAndDeduplicates` for dedup and ordering, and `AvailabilityIT.slotsNameEveryEligibleEmployee` for attribution, which is only visible over HTTP |
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
| 2.14 | Services, employees, working hours and blocked times can each be created, listed and deleted through the API. Employee↔service links are **replaced as a set** rather than created and deleted individually, so the decider is that dropping a service from the set actually drops it | `BusinessConfigurationIT` |
| 2.15 | A service duration must be positive and a whole number of minutes; a zero or negative duration is refused with 400 | `BusinessConfigurationIT.refusesNonPositiveDuration` |
| 2.16 | A working window whose end is not after its start is refused with 400 | `BusinessConfigurationIT.refusesInvertedWorkingWindow` |
| 2.17 | Every new route added in this turn is tenant-scoped and refuses a caller who is not a member | `TenantIsolationIT` — generated from the route table, so this criterion cannot be met by forgetting a route |
| 2.18 | An employee, service, working window or blocked time belonging to another business cannot be read, modified or deleted, even by id | `TenantIsolationIT.crossTenantResourceIsRefused` |
| 2.19 | Deleting a service removes its employee links and does not orphan rows | `BusinessConfigurationIT.deletingAServiceRemovesItsLinks` |
| 2.23 | A caller refused access to a business receives 403 with the standard error body — never 401, which would tell them to authenticate when they already have and it cannot help | `TenantIsolationIT`, `AccessDeniedContractIT` |
| 2.24 | Authorization is decided before argument validation: an outsider addressing a tenant-scoped route with missing or malformed query parameters still receives 403, not 400 | `AccessDeniedContractIT.authorizationPrecedesValidation` |
| 2.25 | Schema naming holds both ways: every `*_at` column is `timestamptz` and every `*_local` column is `time without time zone` | `SchemaConventionsIT` |
| 2.26 | The status code the OpenAPI document declares for an operation is the status that operation actually returns: creates declare 201, deletes declare 204 | `OpenApiIT.documentedStatusCodesMatchReality` |
| 2.27 | The availability response states the step it was computed on, so a client knows what grid it received | `AvailabilityIT.responseStatesTheStep` |
| 2.28 | The API answers a cross-origin preflight from the dashboard's configured origin, and refuses one from any other origin | `dashboard.spec.ts`, `CorsContractIT` |
| 2.29 | No single request can be made arbitrarily expensive by rows a caller creates: duplicate working windows are refused, overlapping windows and busy periods are merged before stepping, and each table has a per-business row cap | `AvailabilityCalculatorTest`, `BusinessConfigurationIT.refusesDuplicateWorkingWindow`, `.refusesRowsPastTheLimit` |
| 2.30 | Every `/api` route is rate limited, not only `/api/auth` | `AuthRateLimitIT` |

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

**Tenant access is decided in the security filter chain, not by `@PreAuthorize`.** The first
version of this document said the opposite, and building it showed why that was wrong: method
security runs *after* Spring has resolved the handler's arguments, so a request missing a required
query parameter was answered 400 before anyone asked whether the caller was entitled to the business
at all. Authorization belongs before input validation, or an outsider gets to probe what an endpoint
accepts. Moving it also removes the silent failure `@PreAuthorize` carries — an annotation that does
nothing when method security is disabled, or when the method is called from within the same bean,
looks exactly like one that works. It remains one bean, one call shape, one place to audit.

**Column naming is a checkable convention, not a habit:** `*_at` is an instant stored `timestamptz`;
`*_local` is a wall-clock time in the business's own zone stored `time`. Both halves are asserted.

**A working window whose local start falls in the skipped hour is clipped to the first real instant, and the grid is stepped from there** — rather than anchored to the configured local start with non-existent times skipped. The two readings agree whenever the step divides the gap and diverge otherwise; this one is chosen because it never offers a time that did not happen, which is the direction part 1 says to resolve toward.
A rule enforced in only one direction would let a genuine instant stored as `time` pass unnoticed,
which is how a booking ends up an hour out twice a year.

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

All thirty criteria in part 2 are true, `docs/audit/turn-2.md` records the five Merge-Readiness
criteria with evidence, and the branch merges to `main` with CI green.

---

## Revision log

| Date | Change |
|---|---|
| 2026-09-05 | First version, written before any implementation commit. |
| 2026-09-05 | Three criteria added after the security review, and one after the browser tests. 2.29 and 2.30 exist because the engine's cost was quadratic in rows a caller can create — twenty thousand identical working windows and twenty thousand overlapping blocks made one request roughly 10^10 comparisons, which no rate limit can help with because the cost sits inside a single request — and because rate limiting reached nothing this turn added. 2.28 exists because the browser tests found the dashboard could not load *any* data: a CORS preflight carries no credentials by construction, the filter chain answered it 401, and the shipped compose file serves the two on different ports. **The API was correct and unreachable, and only a browser could tell us.** |
| 2026-09-05 | Revised after the test writer reported six ambiguities. 2.7 now names two deciders, because the one it named could see neither half of what it asked for. 2.14 says employee↔service links are replaced as a set, which is what the API actually models. Part 3 now states which reading of 2.12 is intended — a window starting in the skipped hour is clipped to the first real instant — since the two readings agree only when the step divides the gap. Added 2.26 and 2.27 from two real defects the report exposed: the OpenAPI document declared 200 for every create and delete while the code returns 201 and 204, so the contract was lying about the API it describes and a generated client would be wrong; and the availability response never stated the step it was computed on, leaving a client unable to know what grid it received. **`CLAUDE.md`'s error-shape rule was also imprecise enough that the test writer resolved it by assertion and had to walk that back — it now says `fieldErrors` appears only on validation failures.** |
| 2026-09-05 | Part 3 rewritten on two points the implementation disproved, and three criteria added. Tenant access moves from `@PreAuthorize` to the security filter chain, because method security runs after argument resolution and an outsider was getting 400 for a missing parameter before the tenant check ran at all (2.24). Moving it exposed a second defect: Spring's default `AccessDeniedHandler` calls `sendError`, the container re-dispatches as ERROR, every `OncePerRequestFilter` correctly declines to run twice, and the second pass therefore looks anonymous — turning a correct 403 into a 401 telling an authenticated caller to authenticate (2.23). The column-naming convention is settled in both directions rather than excepted for `working_hours` (2.25). **The first two were defects in this document's architecture section, found by building it.** |
