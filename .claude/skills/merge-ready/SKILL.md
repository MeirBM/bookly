---
name: merge-ready
description: Run the Merge-Readiness Pack against the current spiral turn before merging it. Answers the five criteria with evidence and writes docs/audit/turn-N.md. Use at the end of a turn, after the security review, before opening or merging the PR.
---

# Merge-Readiness Pack

The gate a spiral turn must pass to enter `main`. Five criteria. **Each is answered with evidence —
a test name, a CI run, a commit, a file and line — or it is not answered.** Prose is not evidence,
and neither is the author's confidence.

Run this before the merge, never after. A standard applied afterwards bends to fit the work.

## Procedure

Determine the turn number from the branch name, then read `docs/spec/turn-N.md`. Work the five
criteria in order and write the result to `docs/audit/turn-N.md` using the template below.

### 1. Functional completeness — does it match the specification?

Take each testable success criterion from the spec in turn. For each: is it implemented, and what
demonstrates it? A criterion with no demonstration is incomplete, whatever the code looks like.
List any criterion the turn did not reach, and say so plainly rather than reinterpreting the
criterion to fit what was built.

### 2. Sound verification — did the tests come from the specification?

- Which suites were written by `spec-test-writer` from the spec alone? Name the commit.
- Does every spec criterion map to at least one named test?
- Do any tests assert on internals rather than on specified behaviour? Those are implementation
  mirrors and do not count toward this criterion.
- Run the suite and record the result. A suite that has not been run this turn is not evidence.

### 3. Engineering hygiene — does it fit the project's standards?

Check against `CLAUDE.md`: entities not leaving the service layer, DTOs on the controller boundary,
`businessId` on every tenant-scoped repository call, `timestamptz` in new migrations, no edited
committed migration, constructor injection, no single-implementation interface, parameterised
logging with no secret in it. Name each violation with file and line.

### 4. Rationale — did someone write down why?

- Does the spec state the goal **and its reason**?
- Do the commit messages explain *why*, given the diff already shows *what*? Read them and say
  whether they would still explain the change in six months.
- Is every decision that closed off an alternative recorded somewhere a reader can find?

### 5. Auditability — can the whole trail be followed?

- Spec committed before the first implementation commit of the turn (check the timestamps).
- The security review's findings recorded, each either fixed with a linked commit or accepted with
  a written reason.
- CI green on the branch head, with the run linked.
- The turn's changes readable as a sequence of atomic commits rather than one dump.

## Output

Write `docs/audit/turn-N.md`:

```markdown
# Turn N — Merge-Readiness Audit
Branch · commit range · CI run · date

## 1. Functional completeness
| Spec criterion | Status | Evidence |
## 2. Sound verification
Suites, who wrote them, which commit, run result.
## 3. Engineering hygiene
Violations found, with file and line. "None found" only if the checks were actually run.
## 4. Rationale
## 5. Auditability
## Verdict
Ready to merge / not ready, and for each criterion not satisfied, exactly what is missing.
```

## The one rule for this gate

If a criterion is unsatisfied, the verdict is **not ready** — including at an inconvenient hour with
a deadline in the morning. The day the gate is skipped is the day it would have caught something.
Record what is missing and let the human decide to override it explicitly; do not soften the verdict
so it reads as passing.
