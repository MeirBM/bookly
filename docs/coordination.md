# Coordination Design

> Module 15 deliverable: written before any agent runs, and proportionate to its size. Four roles,
> not a dozen — `Project_Context.md` §3 and §33 both forbid machinery added to look sophisticated,
> and M14 says the same of orchestration: *"orchestrating everything mistakes the machinery for the
> goal."* The arrangement below exists for one reason the tool's own division cannot supply:
> **whoever wrote the work cannot judge the work.**

## Why any arrangement at all

A single agent that writes the code and then writes the tests will write tests that pass. It reads
its own implementation to decide what to assert, so the suite records what the code *does* rather
than what the specification *wanted*. The result is a green suite that proves the code agrees with
itself — and a later engineer who fixes the real bug sees tests fail.

Two of the four roles below exist purely to break that loop. They are separated by **what they are
shown**, not by what they are told, because an instruction not to peek is not a control.

## The roles

| Role | Input | Output | Boundary | If it fails |
|---|---|---|---|---|
| **Implementer** (main session) | The turn's spec, `CLAUDE.md`, the existing code | Working code on the turn's branch | Does not write the tenant-isolation or availability suites. Does not edit `docs/spec/*` mid-turn — a spec change is its own commit with a reason. | Stop and return to the last good commit. A second correction on top of a failing correction is the drift spiral, not a fix. |
| **Spec-test-writer** (`.claude/agents/spec-test-writer.md`) | `docs/spec/turn-N.md` and the OpenAPI contract **only** | JUnit tests | **Never shown the implementation.** May not read `src/main/java`. Writes only under `src/test/java`. | If it cannot write a test because the spec is ambiguous, it reports the ambiguity instead of guessing. That report is a defect in the spec and is fixed there first. |
| **Security-reviewer** (`.claude/agents/security-reviewer.md`) | The turn's diff | Findings, ranked, with a concrete failure scenario each | Reviews; does not fix. A reviewer that silently fixes teaches nothing and reviews its own work next time. | A finding it cannot confirm is reported as unconfirmed rather than dropped or inflated. |
| **Merge-readiness check** (`.claude/skills/merge-ready/`) | The turn's branch, spec and CI run | The five criteria, each answered with evidence or refused | Cannot mark a criterion satisfied from prose. A criterion with no test name or CI link is not satisfied. | Any unsatisfied criterion blocks the merge. The gate is not negotiable at the hour it becomes inconvenient. |

## How work passes between them

Handoffs are files in the repository, not conversation. The spec is a committed file; the tests are
committed files; the findings are written into `docs/audit/turn-N.md`. This is deliberate — M15:
structure lost at a handoff has to be guessed back by the next agent, and the guess is silent. A
committed artefact can also be read by the reviewer later, which conversation cannot.

**No agent is shown another agent's conclusion.** The security reviewer does not read the
implementer's explanation of why the code is safe, and the test writer does not read either. Models
continue the confident text they are shown; showing one agent another's verdict buys one opinion
stated twice.

## What it costs and what it buys

Four roles across three turns, each with its own context. The cost is roughly a third more tokens
than a single session doing everything, most of it re-reading the spec. What it buys is the only
independent check in the project: a test suite that can fail, and a review that has no stake in the
code passing.

## Where this arrangement is weakest

Both the implementer and the test-writer read the same specification. Neither survives a fault
*in the specification itself* — if the spec says the wrong thing, they will agree perfectly about
the wrong thing. That is the residual risk, and the only defence is the human reading the spec
before the turn starts.
