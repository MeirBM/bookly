# Turn 2 — Merge-Readiness Audit

**Branch** `turn-2-availability` → `main` · **Spec** [`docs/spec/turn-2.md`](../spec/turn-2.md) (30 criteria)
· **Date** 2026-09-05

Each criterion is answered with a test name, a commit or a linked CI run, or it is not answered.

---

## 1. Functional completeness — does it match the specification?

30 of 30 criteria implemented and demonstrated.

| # | Criterion | Test |
|---|---|---|
| 2.1 | A service is never offered into a gap too short for it | `AvailabilityCalculatorTest.doesNotOfferAServiceIntoAGapTooShortForIt` |
| 2.2 | No slot overlaps a busy interval; a slot may start exactly when one ends | `.neverOverlapsABusyInterval`, `.mayStartExactlyWhenABusyIntervalEnds` |
| 2.3 | No slot falls outside the working window | `.staysInsideTheWorkingWindow` |
| 2.4 | Two windows on one day leave a break between them | `.respectsABreakBetweenTwoWindows` |
| 2.5 | Starts are generated on the configured step | `.generatesStartsOnTheConfiguredStep` |
| 2.6 | An unlinked employee contributes nothing | `AvailabilityIT.unlinkedEmployeeContributesNothing` |
| 2.7 | "Any employee" unions, deduplicates and attributes | `.anyEmployeeUnionsAndDeduplicates` + `AvailabilityIT.slotsNameEveryEligibleEmployee` |
| 2.8 | A non-working day returns empty, not an error | `.aNonWorkingDayReturnsNoSlots` |
| 2.9 | No table stores generated slots | `SchemaConventionsIT.noPreGeneratedSlotTable` |
| 2.10 | Spring-forward loses exactly the skipped hour | `.springForwardLosesExactlyTheSkippedHour` |
| 2.11 | Fall-back gains an hour, no duplicate instants | `.fallBackGainsAnHourWithoutDuplicateInstants` |
| 2.12 | No slot at a non-existent local time | `.doesNotOfferANonExistentLocalTime` |
| 2.13 | The zone comes from the business | `AvailabilityIT.zoneIsTakenFromTheBusiness` |
| 2.14 | Each resource can be created, listed and deleted | `BusinessConfigurationIT` — 5 tests |
| 2.15 | A non-positive duration is refused | `.refusesNonPositiveDuration` |
| 2.16 | An inverted working window is refused | `.refusesInvertedWorkingWindow` |
| 2.17 | Every new route is tenant-scoped | `TenantIsolationIT` — **20 generated cases** |
| 2.18 | Another business's row is unreachable by id | `TenantIsolationIT.crossTenantResourceIsRefused` |
| 2.19 | Deleting a service removes its links | `.deletingAServiceRemovesItsLinks` |
| 2.20 | Four distinguishable states on every screen | `dashboard.spec.ts` — all four screens |
| 2.21 | A new business is guided, not shown empty tables | `dashboard.spec.ts.newBusinessIsGuided` |
| 2.22 | Availability shows real slots or says there are none | `dashboard.spec.ts.availabilityShowsRealSlotsOrSaysThereAreNone` |
| 2.23 | A refusal is 403 with the standard body, never 401 | `AccessDeniedContractIT`, `TenantIsolationIT` |
| 2.24 | Authorization precedes argument validation | `AccessDeniedContractIT.authorizationPrecedesValidation` |
| 2.25 | `*_at` is `timestamptz`, `*_local` is `time` | `SchemaConventionsIT` — 3 tests |
| 2.26 | Declared status codes match observed ones | `OpenApiIT.documentedStatusCodesMatchReality` — 21 operations, zero skips |
| 2.27 | The response states its step | `AvailabilityIT.responseStatesTheStep` |
| 2.28 | The preflight is answered for the dashboard origin and refused for others | `CorsContractIT` — 3 tests, plus `dashboard.spec.ts` |
| 2.29 | No request can be made arbitrarily expensive | `AvailabilityCalculatorTest.mergesOverlappingWindowsAndBusyPeriodsBeforeStepping`, `BusinessConfigurationIT.refusesDuplicateWorkingWindow`, `.refusesRowsPastTheLimit` |
| 2.30 | Every `/api` route is rate limited | `AuthRateLimitIT.everyApiRouteIsRateLimited` |

**Nothing is carried forward unmet.** Turn 1 left criterion 1.17 resting on manual verification; it was closed before that turn merged, and this turn leaves no equivalent.

---

## 2. Sound verification — did the tests come from the specification?

Every suite was written by an agent that was never shown `backend/src/main/` or `frontend/src/`. It worked from `docs/spec/turn-2.md`, `docs/api/turn-2-openapi.json` and the Flyway migrations.

**The DST criteria are the reason the separation matters here.** The engine is arithmetic, and a test written by reading it would re-state that arithmetic. Instead the transition date, its direction and its size are read from `java.time`'s own `ZoneRules` at runtime, and the expected slot difference is *computed* as `transitionDuration / step`. Each test states its preconditions — the day really is 23 or 25 hours; the local time really has no valid offset — so a failure says whether the engine is wrong or tzdb moved.

**What independent verification found this turn:**

- **CORS.** No dashboard screen could load any data. A preflight carries no credentials, the chain answered it 401, and `docker-compose` serves the two on different ports — so the shipped deployment was broken. **113 backend tests were green at the time**, because they speak HTTP directly, where preflights do not exist.
- **A duplicate working window answered 500** with a stack trace per attempt: V4's constraint refused the row and the caller was told the server had broken.
- **The availability screen had no error state** when its service list failed — it sat on "Loading availability…" indefinitely. Found only by extending 2.20 to screens that had not been covered.
- **Six ambiguities**, of which two were defects in the running system (the OpenAPI document declared 200 for every create and delete; the availability response never stated its step), one was a defect in `CLAUDE.md`, and three were imprecision in this spec.

**Two moments worth recording, because they are what makes the arrangement worth its cost:**

1. The test writer wrote an assertion about `fieldErrors`, watched it fail on four routes, replaced it — and then **said it had done so**, calling it "me resolving an ambiguous sentence by assertion". The rule was at fault and was fixed. A quietly weakened assertion is the one failure that would make this whole arrangement worthless.
2. Told its fixture was stale rather than the app wrong, it agreed — but noticed the stale fixture had been **accidentally covering a real property** (a session the server rejects should end at the login form), and wrote that as a named test before deleting the fixture.

It also corrected two of its own tests by fixing the *fixture* rather than the expectation: a merge test whose blocks ran to `11:00:59`, so the engine was right to withhold the 11:00 slot, and a row-cap fixture that was off by one.

**Run result:**

```
./mvnw -B verify        39 unit, 81 integration        BUILD SUCCESS
npm run test:e2e        11 browser                     11 passed
```

131 tests: real PostgreSQL 16 and Redis 7 via Testcontainers, no H2; browser tests against the standalone build and the real API.

---

## 3. Engineering hygiene — does it fit the project's standards?

Checked against `CLAUDE.md`; the checks were run, not assumed.

- No entity leaves the service layer; all responses are records.
- **No unscoped `findById` on any turn-2 entity.** Every lookup filters on both ids — the security reviewer verified this independently across all four repositories.
- No request DTO carries a `businessId`.
- `*_at` columns are `timestamptz` and `*_local` columns are `time`, asserted both ways.
- No committed migration was edited; V3 and V4 exist because V2 was already committed and the hook refuses the edit.
- Constructor injection throughout; no single-implementation interface.
- Every screen that loads data renders four distinguishable states, through one shared component rather than a convention repeated per screen.

**One deviation, deliberate and named.** `ServiceOffering` rather than `Service`, because `Service` collides with Spring's stereotype annotation in every file touching both. It is the one place the code departs from the domain word, and it departs to stay readable.

---

## 4. Rationale — did someone write down why?

- The spec states the goal and its reason, and part 1 says how to resolve unanticipated forks: **toward refusing to offer a slot.** A slot wrongly withheld costs one booking; a slot wrongly offered costs a customer's trust and the owner's morning.
- Four of nine pitfalls are about time, because that is where this breaks.
- The commit messages carry the *why*. Worth reading: `9e3982e` (two defects the guardrails forced out, including a correct 403 turning into a 401 through an ERROR re-dispatch), `d2596e2` (the security review, including why a trigger was chosen over a composite foreign key), and `36bf54e` (an API that was correct and unreachable).
- Decisions that closed off an alternative are recorded where a reader meets them: the calculator not knowing what makes an interval busy; a break as two windows rather than a break table; explicit CORS origins rather than a wildcard, with the reason the wildcard is tempting.
- **The revision log separates defects in the specification from defects in the code.** Two architecture points in part 3 were disproved by building them, and say so.

---

## 5. Auditability — can the whole trail be followed?

- **Spec committed before implementation**: `bbc27df` ("docs: specify turn 2 before implementing it") is the first commit on this branch and precedes `1cccd7d`, the first implementation commit — verifiable by `git log --format='%h %ad %s' --date=iso --reverse turn-2-availability ^main`.
- **Security review findings recorded**, each fixed with a linked commit: two HIGH, two MEDIUM, two LOW, all in `d2596e2`. Tenant isolation and IDOR were reported clean, and that is recorded too — a review that only ever finds things is not being read carefully either.
- **CI green on all four jobs**, including the new browser job against a real API:
  https://github.com/MeirBM/bookly/actions/runs/33990543291 — 39 unit, 81 integration, 11 browser, each confirmed to have actually executed rather than been skipped.
- Changes are a sequence of atomic commits.

### What went wrong in the process

1. **A Flyway checksum mismatch stopped the local application starting**, because V3 was revised while still uncommitted after an earlier version had already been applied to the development database. Harmless — tests use fresh containers — but it is the same class of problem the immutability rule exists to prevent, one step earlier.
2. **`git add -A` twice swept up another agent's in-progress files**, and both times the pre-commit hook refused the commit rather than letting half-written tests through. The gate worked; the habit did not.
3. **The first attempt at widening the secret pattern in turn 1 had to be reverted**, and this turn confirmed why: three more legitimate fixtures needed markers, and each one is a chance to reach for `--no-verify`.

---

## Verdict

**Ready to merge.** All 30 criteria are demonstrated by a named test or a linked CI run; none rests on a claim.

The property this turn existed to establish — that the system can say when a business is genuinely free — is verified by 11 unit tests over a pure function, including three that read daylight-saving transitions from the JVM rather than from anyone's memory of when the clocks change.
