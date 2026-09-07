# Turn 4 — Calendar Hand-off, Booking Alerts, and Business Identity

> Module 10 specification, kept short because the work is small. Three features that make Bookly
> feel like a product someone runs rather than a system someone demonstrates. The five parts are
> still here; they are just brief.

---

## 1. Goal, and the reason for it

**Goal.** A customer can put their booking into the calendar they already use. An owner watching
the dashboard learns about a new booking without refreshing. A business looks like itself rather
than like Bookly.

**Reason.** The first three turns proved the hard part — that the times offered are real and that
two people cannot take one slot. None of that is visible to the people using it. A confirmation
screen a customer must transcribe into their own calendar is a booking they may still miss; an
owner who only learns of a booking by reloading is still doing the polling themselves; and a
booking page that carries no sign of the business asks a customer to trust an address.

**How to resolve unanticipated forks.** These are conveniences. **Where a convenience would cost
correctness, honesty or reach, drop the convenience.** A calendar hand-off that silently produces
the wrong time is worse than no button, and an alert that implies Bookly can reach someone with the
browser closed is worse than no alert.

---

## 2. Testable success criteria

### Calendar hand-off

| # | Criterion | Decided by |
|---|---|---|
| 4.1 | The confirmation offers Google and Apple/Outlook, and neither needs an account, a permission or a backend call | `booking.spec.ts.aConfirmedBookingCanBeAddedToACalendar` |
| 4.2 | The `.ics` file names the service, the business, the employee and the exact start and end, in UTC | `IcsFileTest` (unit) |
| 4.3 | The Google link carries the same start and end as the `.ics`, so the two cannot disagree | `IcsFileTest.googleLinkAgreesWithTheIcsFile` |
| 4.4 | A booking in a business whose zone is far from the viewer's exports the *business's* time, not the viewer's | `IcsFileTest.exportsTheBusinessTimeNotTheViewers` |

### Booking alerts

| # | Criterion | Decided by |
|---|---|---|
| 4.5 | Bookings already present when the dashboard opens raise no alert | `dashboard.spec.ts.existingBookingsDoNotAlert` |
| 4.6 | A booking created while the dashboard is open raises exactly one toast, naming the customer, the service and the time | `dashboard.spec.ts.aNewBookingRaisesOneToast` |
| 4.7 | The same booking never alerts twice, however many polls occur | same test, asserted across two poll cycles |
| 4.8 | A toast can be dismissed, and disappears on its own | `dashboard.spec.ts` |
| 4.9 | The dashboard states that alerts arrive only while it is open, unobtrusively and without implying otherwise | `dashboard.spec.ts` |
| 4.10 | Polling stops when the owner leaves the business screens | reviewed; TanStack Query unmounts the interval with the component |

### Business identity

| # | Criterion | Decided by |
|---|---|---|
| 4.11 | An owner can set and clear a logo by URL, and it appears on the public booking page and in the dashboard | `BusinessLogoIT`, `booking.spec.ts` |
| 4.12 | A business with no logo shows the Bookly mark rather than a gap | `booking.spec.ts.aBusinessWithoutALogoShowsTheBooklyMark` |
| 4.13 | Only `https` URLs with a host and no embedded credentials are accepted; `http`, `javascript:` and `data:` are refused with 400 | `BusinessLogoIT.refusesAUrlThatIsNotHttp` |
| 4.14 | A logo that fails to load falls back to the Bookly mark rather than a broken image | `booking.spec.ts.aBrokenLogoFallsBackRatherThanBreaking` |
| 4.15 | Setting a logo is tenant-scoped like every other write | `TenantIsolationIT`, generated from the route table |

---

## 3. Architectural guidance

**The calendar hand-off is entirely client-side.** An `.ics` file is a few lines of text and a
Google link is a URL; neither needs an account, an OAuth consent screen, a stored token or a
backend route. Both are generated from one function so the two exports cannot drift into
disagreeing about when the appointment is.

**Alerts reuse the query layer rather than adding one.** TanStack Query's `refetchInterval` is the
polling infrastructure this project already has; a hand-rolled `setInterval` would duplicate it and
would also have to reimplement pausing on a hidden tab and cleaning up on unmount, both of which
come free. **No backend change.** The owner's appointment list is already tenant-scoped, and
watching it is a read they are already entitled to make.

**The logo is a URL, not a file.** Upload needs object storage: a Railway container has no
persistent disk, so an uploaded file would vanish on the next deploy — a feature that works until
it silently does not. A URL column is one migration and one field. The trade is that the image
depends on someone else's hosting, which is why 4.14 requires a fallback rather than trusting it.

---

## 4. Validation approach

- **Unit, no framework:** the `.ics` and Google-link builders, including a business zone far from
  the runner's.
- **Integration, Testcontainers:** setting, clearing and rejecting a logo; tenant scoping via the
  existing route-table guardrail.
- **Browser:** the calendar controls on a real confirmation, an alert raised by a booking made
  out-of-band while the dashboard is open, and the logo appearing and falling back.
- **Written by `spec-test-writer` from this document**, with no access to `src/main` or
  `frontend/src`.

---

## 5. Known pitfalls

1. **An `.ics` needs CRLF line endings and folded long lines.** Calendar clients that reject a file
   do so silently, and the reader concludes the button is broken.
2. **`DTSTART` must be UTC with a `Z`,** or the appointment lands in whatever zone the reader's
   machine is in — the same class of bug turn 2 spent four pitfalls on.
3. **A first poll is not a change.** Every booking looks new to an empty set, so the first
   successful response seeds what is known and alerts nothing. Getting this wrong greets an owner
   with a toast for every booking they already had.
4. **A toast is an interruption.** It must be dismissible, must expire, and must never stack up
   into a wall — a notification that cannot be got rid of is a worse experience than no
   notification.
5. **`javascript:` and `data:` URLs in an `<img src>`** are the obvious injection here, and the
   logo is owner-supplied text rendered on a public page. `http` is the non-obvious one: it passes
   every validator that thinks in schemes, and then fails in the browser, because a cleartext image
   on a TLS page is blocked or upgraded and the owner is left looking at the fallback with no
   explanation.
6. **Do not imply what polling cannot do.** The owner must not believe Bookly will tell them about
   a booking while their browser is closed. It will not, and finding that out through a missed
   appointment is the worst possible way to learn it.

---

## Definition of done

All fifteen criteria are true, `docs/audit/turn-4.md` records the five Merge-Readiness criteria with
evidence, and the branch merges to `main` with CI green.

---

## Revision log

| Date | Change |
|---|---|
| 2026-09-07 | First version, written before any implementation commit. |
| 2026-09-07 | **Spec defect, found by the security review.** 4.13 said `http(s)`, which the implementation honoured. Accepting `http` is a promise Bookly cannot keep: it is served over TLS, so browsers block or fail to upgrade a cleartext image and the owner silently gets the Bookly mark instead of their logo — and where it does load, anyone on the visitor's path chooses what the shop's public page shows. Narrowed to `https`, and to URLs carrying no userinfo, since a logo is republished to anonymous callers by the public endpoint and `https://user:pass@host/x.png` would publish that credential to anyone who asks. This is the spec's own fork rule applied: the convenience of accepting `http` cost correctness and honesty, so the convenience goes. |
