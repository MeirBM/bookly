# Turn 4 — Merge-Readiness Audit

**Branch** `turn-4-polish` → `main` · **Spec** [`docs/spec/turn-4.md`](../spec/turn-4.md) (15 criteria)
· **Date** 2026-09-07

Each criterion is answered with a test name or recorded as unanswered. Turn 3's audit claimed five
tests that did not exist; the discipline that caught it — look the decider up before writing the
row — is applied here to every line.

---

## 1. Functional completeness

### Calendar hand-off (4.1–4.4) — all met

| # | Criterion | Test | Result |
|---|---|---|---|
| 4.1 | Google and Apple/Outlook offered, needing no account, permission or backend call | `booking.spec.ts.aConfirmedBookingCanBeAddedToACalendar` | pass |
| 4.2 | The `.ics` names service, business, employee and the exact start and end, in UTC | `ics-file.spec.ts.theIcsNamesTheServiceBusinessEmployeeAndTheExactStartAndEndInUtc` | pass |
| 4.3 | The Google link carries the same start and end as the `.ics` | `ics-file.spec.ts.googleLinkAgreesWithTheIcsFile` | pass |
| 4.4 | A far-away business zone exports the business's time, not the viewer's | `ics-file.spec.ts.exportsTheBusinessTimeNotTheViewers` | pass |

**4.1 is asserted operationally, not by reading the markup.** "Needs no backend call" is checked by
counting requests to the API from the moment the confirmation renders: the assertion is zero. The
test then decodes the `data:` URL and requires the file to name the seeded service, business and
employee at the UTC stamp the API recorded — so it also proves the page hands the builder the right
event rather than merely rendering two buttons.

**4.4 would have passed vacuously and the test-writer noticed.** These are pure functions running in
Node, so Playwright's `timezoneId` cannot reach them. The test pins `process.env.TZ` to
`Pacific/Kiritimati` (+14) and `Pacific/Midway` (−11) around each build and demands byte-identical
output. On a UTC runner the naive version of this test passes without testing anything.

**4.3 is asserted against the `.ics`, not against a second constant.** The Google link's stamps are
compared to the values read back out of the file. Two hard-coded expectations can be wrong together
and still agree with each other; this cannot.

### Booking alerts (4.5–4.10) — all met

| # | Criterion | Test | Result |
|---|---|---|---|
| 4.5 | Bookings already present when the dashboard opens raise no alert | `dashboard.spec.ts.existingBookingsDoNotAlert` | pass |
| 4.6 | A new booking raises exactly one toast, naming customer, service and time | `dashboard.spec.ts.aNewBookingRaisesOneToast` | pass |
| 4.7 | The same booking never alerts twice, across two poll cycles | same test | pass |
| 4.8 | A toast can be dismissed, and disappears on its own | `dashboard.spec.ts.aToastCanBeDismissedAndAlsoDisappearsOnItsOwn` | pass |
| 4.9 | The dashboard states alerts arrive only while it is open | `dashboard.spec.ts.theDashboardSaysAlertsArriveOnlyWhileItIsOpen` | pass |
| 4.10 | Polling stops when the owner leaves the business screens | reviewed, not tested — see below | met |

Bookings are created out-of-band through the public API while the dashboard is open, the pattern
3.19 established. There are no fixed sleeps: a helper counts poll *cycles* and `expect.poll` waits
on those, so "across two poll cycles" is asserted against polls that demonstrably happened.

**4.7 counts appearances, not the final screen.** Because 4.8 requires the toast to expire, a
snapshot at the end cannot distinguish "alerted once correctly" from "alerted twice and both
expired". The test counts transitions from no matching alert to one.

**Two vacuity holes were found and closed by the test-writer, in its own tests.** 4.5 and 4.7
originally counted HTTP requests, which the dashboard's initial four-endpoint load satisfied
instantly — 4.5 "passed" in 1.1 seconds while testing nothing. And 4.14 would have passed on a page
that ignored `logoUrl` entirely, so it now establishes that a working logo renders before breaking
it. Both are recorded here rather than quietly fixed, because a test that passes for the wrong
reason is the failure mode this whole arrangement exists to catch.

**4.10 is the one criterion decided by reading rather than by a test, and the spec says so.** The
alert query is TanStack Query's own `refetchInterval` on a hook mounted in the business layout;
the interval is owned by the query, and unmounting the layout unmounts the query with it. There is
no `setInterval` to leak. Asserting the absence of network traffic after navigation is possible but
would assert the framework's behaviour rather than Bookly's.

### Business identity (4.11–4.15) — all met

| # | Criterion | Test | Result |
|---|---|---|---|
| 4.11 | Set and clear a logo; it appears on the public page and the dashboard | `BusinessLogoIT` (5 cases), `booking.spec.ts` | pass |
| 4.12 | No logo shows the Bookly mark rather than a gap | `booking.spec.ts.aBusinessWithoutALogoShowsTheBooklyMark` | pass |
| 4.13 | Only `https`, with a host, no credentials; otherwise 400 | `BusinessLogoIT.refusesAUrlThatIsNotHttpsWithAHostAndNoCredentials` (16 URLs) | pass |
| 4.14 | A logo that fails to load falls back rather than breaking | `booking.spec.ts.aBrokenLogoFallsBackRatherThanBreaking` | pass |
| 4.15 | Setting a logo is tenant-scoped | `TenantIsolationIT.settingALogoIsTenantScoped` | pass |

**4.13 refuses sixteen URLs, not the two the criterion named.** Mixed-case `JaVaScRiPt:` and
`HtTp:`, protocol-relative `//host` whose scheme the page picks rather than the owner, a scheme-less
string, `file:`, `ftp:`, host-less `https://`, and userinfo in both `user:pass@` and bare `user@`
forms. That is the difference between an allow-list and a block-list of the two schemes that
happened to be written down. Every refusal is followed by reading the column back: a 400 that still
wrote the row is not a refusal.

**4.15 asserts the same thing about authorization.** Not merely that a non-member receives 403, but
that the victim's logo is unchanged afterwards and that a business the caller cannot see answers
byte-identically to one that does not exist.

---

## 2. Verification — what was actually run

- Backend: 39 unit + 123 integration, `BUILD SUCCESS`.
- Browser: 33 Playwright tests, and again under `--repeat-each=2` to check the alert tests for
  flakiness. The three timing-sensitive alert tests were consistent across runs.
- Frontend: `tsc --noEmit`, `eslint`, `next build` clean.

**Tests were written by agents that had not seen the implementation**, per
[`docs/coordination.md`](../coordination.md) — `backend/src/main` and `frontend/src` both withheld,
with one narrow exception written into the agent definition this turn: a unit test of a pure
function must import it, so export signatures in `frontend/src/lib/` may be grepped, never bodies
and never a component.

**Process deviation, recorded rather than hidden.** The project's `spec-test-writer` and
`security-reviewer` agent definitions were not registered in this session's agent list, so their
contracts were carried inline in the prompts instead. The boundary held — the reports show the
agents refusing to read what they were told not to, and the test-writer declining to edit the spec
to match its own rename — but the enforcement was a prompt rather than a tool restriction, which is
weaker, and a reader should weigh the evidence accordingly.

---

## 3. Security review

The reviewer read the full diff and returned eight findings. Six were fixed; the reasoning for each
is in commit `aa01f38`. Two of them were real bugs in code that had already passed `tsc`, `eslint`,
a build and 21 tests:

- **The `.ics` semicolon escape was a no-op.** `"\;"` is not an escape sequence in JavaScript; it
  evaluates to `";"`, so the line replaced every semicolon with itself — immediately below a
  correctly doubled `"\\,"`. A service named `Cut & Colour; Deluxe` wrote an unescaped
  property-parameter separator, which strict calendar clients truncate or reject *silently*. This is
  pitfall 1 of this turn's own spec, reached by exactly the route the spec warned about.
- **The alert window was computed in UTC.** `toISOString().slice(0, 10)` is neither the viewer's zone
  nor the business's. An owner in Los Angeles with the dashboard open at 20:00 was already on
  tomorrow's UTC date, so a booking made for 21:30 that evening fell outside the query and raised no
  toast — every evening, for precisely the case 4.6 exists to catch.

**One suggestion was declined with reason.** `crossOrigin="anonymous"` on the logo image switches
the fetch to CORS mode; an image host without `Access-Control-Allow-Origin` — which is most of them
— would then fail to load. It hardens nothing here and breaks the ordinary case. `referrerPolicy`
was adopted; the pair was not.

**One finding revealed a defect in the specification rather than the code.** 4.13 said `http(s)`,
and the implementation honoured it exactly. Accepting `http` is a promise Bookly cannot keep: served
over TLS, a cleartext image is blocked or fails to upgrade, and the owner silently gets the fallback
mark with no explanation. The spec was corrected first, in its own commit (`95808c5`), and the code
and tests followed it — not the other way round.

---

## 4. Residual risk, accepted deliberately

These are consequences of "a logo is a URL, not an upload". They are recorded because an unstated
trade reads as an oversight.

1. **A visitor's browser fetches a host Bookly does not control.** Any business owner can make every
   anonymous visitor to their booking page issue a GET to an address of their choosing, disclosing
   IP, user-agent and timing. `referrerPolicy="no-referrer"` removes the referrer; it cannot remove
   the request, because an `<img>` *is* a request. Closing this properly means proxying or caching
   the image server-side, which needs object storage — the thing the spec ruled out for turn 4 with
   its reason. **Accepted.**
2. **Nothing bounds what is downloaded.** The value is checked to be a URL, never to be an image, and
   an `<img>` pulls whatever is there before deciding it cannot decode it. A multi-gigabyte file
   costs the visitor, not Bookly. Not fixable from inside an `<img>` tag. **Accepted.**
3. **A private-network host is accepted.** `https://192.168.1.5/logo.png` passes. Refusing it
   properly means resolving DNS at save time *and* at fetch time, since a public name can point
   anywhere and be re-pointed afterwards; a check that cannot hold is theatre. Ruled out of 4.13
   explicitly in the spec's revision log rather than left for a test to guess. **Accepted.**
4. **There is no Content-Security-Policy.** An `img-src` allow-list is the real control for 1–3 and
   the repository has none. This is the highest-value item deferred out of this turn, and it belongs
   to whichever turn takes on response headers as a whole rather than being bolted to a logo field.
5. **`TenantGuard` checks membership, not role.** Any `business_members` row can repoint the public
   page's logo. Not exploitable: `BusinessService` is the only creator of a membership and always
   writes `BUSINESS_OWNER`, so no non-owner row can exist. Named because "set what the public page
   displays" is the first write where owner and employee plausibly diverge.

---

## 5. Merge-readiness

| Criterion | Answer |
|---|---|
| **Specification satisfied** | 15 of 15, each with a named test above, except 4.10 which the spec itself assigns to review. |
| **Tests written independently** | Yes, by agents with the implementation withheld — with the registration deviation recorded in §2. |
| **Reviewed** | Yes. Eight findings; six fixed, one declined with reason, one a spec defect corrected in the spec. |
| **CI green** | Pending — recorded on the PR, not claimed here. |
| **Risks stated** | Yes, §4. Five, none silent. |

**A turn-3 test raced in CI and was made to wait, not made to pass.**
`theOwnerSeesAndCancelsABooking` failed on the first CI run. It was not a turn-4 test and turn 4
did not touch it. Its final step read `body.innerText()` once, the instant the *API* reported
`CANCELLED` — allowing the browser's own invalidated refetch, a second round trip, no time at all.
It passed locally in about a second and failed on a loaded runner, which is the signature of a test
measuring the runner rather than the behaviour. The condition was left exactly as written and must
still become true within the same `SETTLE` budget; only the single read became a wait. The change
is called out here because "CI was red and then it was green" is the sentence behind which a
weakened assertion normally hides.

**A process failure worth recording.** Two `git add -A` calls made while a test-writing agent was
still working swept 992 lines of its in-progress browser tests into commits whose messages described
security fixes and a migration. The atomic-commit rule in `CLAUDE.md` is there so a reader six months
out can trust a message; a commit that quietly contains someone else's work breaks that. The branch
was unpushed, so it was re-split into three honest commits after tagging a backup and verifying the
resulting tree was byte-identical to the original. The lesson is narrower than "be careful": do not
use `git add -A` while another agent is writing to the tree.
