# Bookly — Framing Document

> Module 6 deliverable. Written before any code exists, and revised at the head of each spiral
> turn. Each of the four sections carries the test the course attaches to it; the test is stated
> so a reader can apply it to the section rather than take the section on trust.

---

## 1. Problem Statement

**Test: could several different solutions be proposed for it?**

A small appointment-based business — a barber with two chairs, a salon with four staff, a personal
trainer working alone — cannot tell a customer when it is free without a person doing the telling.

The true availability of a particular employee for a particular service is not written down
anywhere. It is the intersection of the business's opening hours, that employee's own working
hours, the services that employee is qualified to perform, how long that specific service takes,
the appointments already booked, breaks, blocked time, and holidays. Nobody maintains this
intersection as a document; the owner recomputes it in their head, per enquiry, while working.

Three consequences follow, and they are the actual problem:

- **Booking costs the owner their attention.** Enquiries arrive as WhatsApp and Instagram messages
  during appointments. Each one interrupts paid work to perform a mental calculation.
- **The calculation is performed under interruption, so it is sometimes wrong.** Two customers are
  given the same slot; a 45-minute service is promised into a 30-minute gap; an employee is booked
  for a service they do not perform.
- **The business is closed to booking whenever the owner is unavailable.** Enquiries arriving at
  22:00, or during a fully booked day, are answered late or not at all. The demand is real and
  unmeasured, because nothing records the enquiries that went unanswered.

Note what this statement does *not* say: it does not say "build a booking website." Several
different solutions could answer this problem — a shared calendar with manual owner approval; a
staffed answering service; an assistant that reads the DMs and replies; a self-service booking page
that computes availability itself. This project chooses the last of those, and that choice belongs
in the specification, not here.

---

## 2. Stakeholder List

**Test: nobody should discover themselves on this list too late.**

| Stakeholder | What they need | Where they appear in the work |
|---|---|---|
| **Business owner** | To stop being the booking system; to trust that what the system promises a customer is actually free | Dashboard; the availability engine's correctness |
| **Employee** | Their real working hours, days off and skills respected; no appointment they cannot perform | `working_hours`, `blocked_times`, `employee_services` |
| **End customer** | To book in under a minute, without an account, and to be told the truth about what is available | Public booking page `/book/{slug}` |
| **A second business on the same deployment** | Never to have their customers, staff or revenue visible to another tenant | Tenant isolation — the security tests in Turn 1 exist for this stakeholder |
| **The course lecturer / reviewer** | To open the repository and verify claims without running a demo or believing prose | Framing, specs, per-turn audits, CI runs, commit history |
| **A future mobile developer** (Android/iOS, `Project_Context.md` §4) | A client-agnostic API that assumes no browser | REST + OpenAPI; no session state, no server-rendered HTML in the contract |
| **A future maintainer** | To understand *why*, not only *what* | Commit messages, the `Why` sections in each spec |
| **The customer as a data subject** | Their name, phone and email held no longer and no wider than the booking requires | Customers stored per-business; no cross-tenant read path |

The reviewer and the future mobile developer are on this list deliberately. Both are stakeholders
who are easy to discover too late: the first decides the grade, and the second decides whether the
API shape chosen this week survives.

---

## 3. Testable Definition of Done

**Test: could two readers disagree about whether it was met?**

Project-level. Each turn adds its own criteria in `docs/spec/turn-N.md`; these are the conditions
for the project as a whole. Every line resolves to one true/false answer.

1. `git log --graph main` shows **three merge commits**, one per spiral turn, each preceded by that
   turn's specification commit and followed by its audit commit.
2. Each turn has a `docs/spec/turn-N.md` committed **before** that turn's first implementation
   commit — verifiable by commit timestamp and order.
3. Each turn has a `docs/audit/turn-N.md` recording all five Merge-Readiness criteria, each backed
   by a named test or a CI run URL rather than by an assertion.
4. Two simultaneous bookings for the same employee and the same time produce exactly one
   appointment; the loser receives HTTP 409. Demonstrated by an automated concurrency test, not by
   inspection.
5. A user authenticated against Business A receives HTTP 403 on every Business B endpoint.
   Demonstrated by a parameterised test that enumerates the tenant-scoped routes.
6. The availability endpoint never returns a start time at which the requested service cannot
   entirely fit, including across a daylight-saving transition.
7. The test suites for the availability engine and for tenant isolation were written from the
   specification by an agent that was not shown the implementation. Recorded in
   `docs/coordination.md` and in the commit that introduces them.
8. CI runs on every pull request and is green on all three merge commits.
9. A visitor can complete a booking end-to-end at the deployed public URL named in `README.md`,
   and the resulting appointment is visible in the owner dashboard.
10. No credential is present anywhere in the git history, verified by the secret scan running in CI
    over the full history, not only the tip.

---

## 4. Out of Scope

**Test: could someone reasonably have expected this in scope?**

Everything below could reasonably have been expected — `Project_Context.md` §34 lists most of it as
the final target. It is excluded from these three turns deliberately, and each exclusion carries its
reason. The governing principle is that the risky, verifiable core (tenant isolation, availability
correctness, booking concurrency) is worth more evidenced than the full feature list is worth
claimed.

| Excluded | Reason |
|---|---|
| Payments, deposits, refunds, webhooks | Correct webhook handling needs signature verification, idempotency and replay tests to be worth anything. Half-built payments are worse than none. |
| Subscription plans, usage limits, billing | Depends on payments. Enforcing limits without charging demonstrates nothing. |
| Email / SMS / push notifications and reminders | Delivery cannot be evidenced without a provider account and deliverability testing. Confirmation is instead shown on screen and persisted in `appointment_status_history`. |
| WhatsApp Business / Cloud API | `Project_Context.md` §13 forbids assuming an external API's capabilities without verifying them; verification requires an approved business account that does not exist yet. |
| Instagram / TikTok / Google integrations, marketing attribution | Depends on the above; attribution without traffic measures nothing. |
| Analytics dashboard | Every metric listed (revenue, no-show rate, employee performance) requires booking history that only exists after the system has been used. |
| Android and iOS applications | Explicitly a later phase (§4). The obligation honoured now is that the API assumes no browser. |
| Google OAuth | Email-and-password already exercises the security work being graded; OAuth adds a provider dependency and no new evidence. |
| Customer accounts and login | Booking is anonymous by design — requiring an account is the friction the problem statement objects to. |
| `SUPER_ADMIN` and `MANAGER` roles | Their permissions are undefined (§7). Two enforced roles are verifiable; five declared ones are not. |
| Postgres row-level security | The stronger second layer under the application's tenant guard, but it complicates Flyway and JPA enough that it could not be properly tested in the time available. The guard plus its test suite is the layer that ships. |
| Kubernetes, Kafka, Elasticsearch, GraphQL, microservices | §3 and §33 forbid them, and nothing in the slice needs them. |

---

## Revision log

| Date | Turn | Change |
|---|---|---|
| 2026-09-05 | Turn 0 | First version, written before any code. |
