# ASE-26 — Agentic Software Engineering [Vibecoding]
### Combined notes, Lessons 1–9 · Mikael Gorsky

Course structure: **4 parts, 19 modules.** These nine lessons cover Modules 1–17.
**Modules 18–19 (Part 4 — the changed market) were not reached in these decks.**

---

## 1. Map of the nine lessons

| # | Date | Modules | Subject |
|---|------|---------|---------|
| **1** | 07.07.2026 | Course frame + **M1** | What agentic software engineering is; equip & verify; the autonomy scale |
| **2** | 13/14.07.2026 | **M2, M3** | The human role (erode/hold/compound); mental models of the agent |
| **3** | 21.07.2026 | **M4, M5** | The seven-part workflow; typology of ADEs (pillars, surfaces, seven questions) |
| **4** | 27.07.2026 | **M6, M7** | Idea → action plan (the problem is found); web application architecture |
| **5** | 03.08.2026 | **M8, M9** | Interface design & documentation; cognified software and the economics of a model call |
| **6** | 10.08.2026 | **M10, M11** | Specification & the co-evolution spiral; context engineering |
| **7** | 17–18.08.2026 | Flow + **M12, M13** | Version control & session drift; verification before trust |
| **8** | 24–25.08.2026 | **M14, M15** | Multi-agent decomposition & orchestration; designing multi-agent workflows |
| **9** | 31.08–01.09.2026 | **M16, M17** | Review, quality & legacy code; building secure software |

> Lesson 6's title slide reads "Lesson 5 · 03.08.2026" — a copy-paste slip; its news slide is dated 10.08.

**Course admin (L1 s6–7):** grading is three equal thirds — class engagement, the running project (the Tribunal), your own project. *"I grade how well you direct the agent — not the app you ship."* **Only what he can open and verify in your repo counts — no verbal claims.** Up to 90 defensible, up to 97 obtainable, 98–100 exceptional. Toolbox: GitHub, Supabase, Netlify, OpenRouter, Claude Code. By L9 the deliverable is stated as **hosted app + GitHub repo**.

---

## 2. The synthesis — the course as one argument

### 2.1 The thesis

> **"Do not ask an agent for software and hope. Equip the agent to do the work, and verify what it returns."** (L1 s21)

Everything else is that sentence unrolled. The casual user asks and hopes; the engineer **equips before** the agent acts and **verifies after**. Equipping lowers the chance of going wrong; verifying catches what went wrong anyway, before it enters the project.

**Why now (L1 s19–20):** speed has outrun trust. One engineer produces in an afternoon what took a week — but reading it with care takes far longer than an afternoon. The evidence he cites: over two-thirds of agent-generated changes delayed in review or never reviewed; an apparent 12% solve rate falling to ~4% under audit; adopting organisations naming trust and reliability as the single leading obstacle.

**The north star (L1 s24):** the output should not merely run, it should be *good software* — functionally suitable, reliable, secure, maintainable, usable, performant, compatible, safe, portable. Modules 16 and 17 each take one of these: **M16 asks whether a later hand understands it; M17 asks whether an adversary can subvert it.**

### 2.2 The collaborator, and its five properties

The agent is treated as **a genuine intelligence, not a tool** (L2 M3) — consciousness left an open question, autonomy taken as real. **Agent = model + tools + loop.** Five models govern everything downstream:

1. **It knows only its context window.** No memory beyond it; each session starts empty. **It cannot tell what it is missing — it fills gaps silently.** More context is not better; attention thins when crowded. → *Module 11.*
2. **It acts only through its tools.** Text reasons; only tools change the world. Tools fix **blast radius**. **To forbid an action, withhold the tool — don't just say no.** → *Modules 5, 17.*
3. **It plans by likely steps, not judgement.** A neat plan can be confidently wrong and cannot be judged by tone. **Reading the plan is the cheapest verification step.** → *Modules 4, 13.*
4. **It fails in four nameable ways.** Hallucination → verify against a source (M13). Misalignment → specification (M10). Ambiguity collapse → make it ask, not assume (M8, M11). Sycophancy → ask it to criticise, not agree.
5. **It is competent along a jagged frontier.** Aces hard tasks, trips on trivial ones; competence follows training data, not human difficulty; **the same confidence whether right or wrong.** Learn where it lies by observation — *calibrated trust*.

Property 1 is the single most reused claim in the course. "It fills gaps silently" is why the reverse interview exists (M6), why a spec has five parts (M10), why only a human supplies the *why* in documentation (M8), and why agent-written code omits the security check (M17).

### 2.3 The human's half

**Skills sort three ways (L2 M2):**
- **ERODE** — writing a function to a precise spec, boilerplate, codebase search, documenting what code already does. *The more fully a task can be specified in advance, the more the agent can do it.*
- **HOLD** — first-of-its-kind debugging in *this* system, weighing a real trade-off between two good designs, knowing who must approve. *The block isn't writing code — it's missing context or judgement.*
- **COMPOUND** — framing the problem, system shape, judging output, deciding what is worth building, taste.

**The boundary is Polanyi's** (*The Tacit Dimension*, 1966): explicit knowledge the agent absorbs from text; tacit knowledge never fully written down. **The test: could this be taught completely from a book?** Yes → erodes. No → compounds.

**Participation ≠ delegation:** agents take part in ~60% of tasks; ~5% can be fully handed off. Across *plan → execute → verify → accept*, AI helps at each and **the human stays responsible everywhere.** Cannot be delegated: defining success, accepting risk, ethical decisions, releasing software, accountability.

**As AI improves: execution shrinks, coordination stays crucial, judgment grows.** Your leverage comes from moving up.

> ⚠️ The deck gives **two different "four hazards" lists.** Text slide: *skill erosion → deliberate practice; black-box codebase → review (M16); responsibility gap → audit trail (M4); model bias → framing & context (M6, M11).* Illustrated cards: *over-trust → skepticism by default; lack of verification → verify relentlessly; context collapse → preserve the context; accountability drift → own the outcome.* Confirm which is examinable.

### 2.4 The unit of work — one spiral turn

Module 4's **seven parts** are the skeleton every later module hangs on:

| # | Part | The rule | Built out in |
|---|------|----------|--------------|
| 1 | **Intent** | What the work truly tries to achieve. *"Add validation" states a solution, not an intent.* | M6 |
| 2 | **Specification** | Intent → precise, checkable decisions. **Every fixed decision is one the agent won't guess.** | M10 |
| 3 | **Context** | Conventions and related code. **The agent cannot see a pattern nobody supplied.** | M11 |
| 4 | **Plan** | Makes the agent's understanding visible early, at the lowest cost of correction. | M7, M15 |
| 5 | **Execution** | Looks like the whole to outsiders. *"The real skill lies in everything surrounding it."* | M9 |
| 6 | **Verification** | Output against the written specification. Speed makes it most necessary and most tempting to skip. | M13 |
| 7 | **Audit trail** | Decisions, actions, verification. **"It most sharply separates engineering from craft."** | M12, M16 |

**The co-evolution spiral (M10)** turns this into a rhythm: revise the intent from what the last turn taught → update the context so the lesson survives → commit and branch → agent builds → verify against criteria → review and record the evidence. At each turn's end, **lock what use confirmed, not what argument confirmed.** Sources: Dorst & Cross (2001), Boehm (1988), Brooks — build, observe, revise.

### 2.5 Equipping, in order

**M6 — the problem is found, not given.** Pólya (*How to Solve It*, 1945): understand before you solve — *"to solve a problem that is not yet understood is only to solve the wrong one faster."* Schön (*The Reflective Practitioner*, 1983): the expert reframes as he works. Brooks (*The Design of Design*, 2010): the requirement was found by first proposing the wrong one.

Four framing questions — **what is it for / should it exist at all / who is it for / what constraints** — and **four written deliverables, each with a test:**

| Deliverable | Test |
|---|---|
| Problem statement | Could several different solutions be proposed for it? |
| Stakeholder list | Nobody should discover themselves on the list too late. |
| Definition of done | Could two readers disagree about whether it was met? |
| Out-of-scope list | Could someone reasonably have expected this in scope? |

**The reverse interview:** give the agent a paragraph, have it interview you one question at a time pressing on every vague answer, then ask for two lists — every question it asked, and **every assumption it made where you said nothing.** That second list is *"a map of the silences in your own thinking."*

**M10 — the specification is your deliverable.** Keep it after the code exists; version it as the primary artefact; generate the code from it. **"Rewrite the specification before you rewrite code."** Knuth's five criteria (1968): finiteness, definiteness, input, output, effectiveness — aimed at, in prose, one sentence per decision.

**The five parts of a spec:** (1) goal **and its reason** — the reason settles unforeseen forks; (2) testable success criteria — countable, one true/false answer each; (3) architectural guidance — boundaries only, three sentences, leave the interior to the agent; (4) validation approach — named and committed *before* building; (5) known pitfalls — *"the warnings you would give a colleague"*, including the ones only you know.

**M11 — context engineering.** *"Assume everyone calls the same model; differentiate through the context you supply."* Sound work from a middling model with good context; wrong work from the best model with poor context.
- The window is a **budget**, finite however large; every token is reprocessed on every call, so a 10k-token file is a recurring tax.
- **Attention falls unevenly — rules at the beginning or end, never the middle.** Repeat what you cannot afford to lose.
- Send large reading to a **subagent** with its own window (6,000 tokens read → 400 returned).
- The advertised window is gross, not usable: system prompt, tool descriptions, context files, and the compaction reserve come off the top. `/context` shows the breakdown. Default 200k; Opus 4.6+ 1M.
- **Tools cost twice** — description tokens on every call, plus one extra decision each. Fifty tools force fifty judgements.
- **Agent Skills** (Oct 2025): one line until used. `disable-model-invocation: true` hides one so it runs only by name — **hide the ones that commit, deploy, or send.** A 2026 survey found a third of shared skills hid malicious instructions.
- Four instruction files: machine-wide, `~/.claude/CLAUDE.md`, `./CLAUDE.md` (in git), `CLAUDE.local.md` (not in git). Read upward through parents; `claudeMdExcludes` drops by pattern. `.claude/rules/` + a `paths` field scopes a rule to matching files.
- **Subtract before you add.** Keep files under 200 lines. Curated human context measured **+4%**; unreviewed generated context **−3% and +20% tokens** — *"write it by hand, or leave it out."*
- **Write every correction down as a rule, not a complaint.** Replace *"the judge returned prose again"* with *"Demand the fixed form twice."*
- **A file advises; a hook enforces.** A `PreToolUse` hook in `settings.json` blocks what Claude decided — commits, deletions, deploys.

### 2.6 The environment and the runtime

**M5 — six pillars:** bare model · **tool augmentation** (what turns a model into an agent) · knowledge and memory · learning from experience (*least settled — claims that an ADE learns deserve close reading*) · multi-agent coordination (*a single agent approving itself has no real check*) · computer use.
**Three surfaces:** command line (transparency, you manage every detail) → editor (reduced friction, less of the inner loop visible) → builder (hides architecture; least transparency, greatest convenience).
**Seven questions for any ADE:** pillars · surface · default blast radius · sandboxing · audit-trail mechanisms · context handling · **model coupling** — *"an open interface promises a substitutability it lacks; real coupling lives in training, not in code."*

**M7 — four parts, one motion:** browser → backend → database → deployment, and the **request cycle** is the anchor. *"A part you cannot place is a part no one has understood yet."* **The browser cannot be trusted** — its code runs in the open; nothing secret and nothing whose correctness must hold belongs there. **Deployment earns respect** — on your laptop a mistake harms one person; deployed, it reaches everyone at once and keeps being served.

**M9 — the model is a runtime component**, not a build-time tool. Kevin Kelly's *cognifying* (*The Inevitable*, 2016). **Four facts: variable, costly, slow, fallible.** Variability means you cannot test one fixed answer. **Draw the boundary well** — most of a cognified system is plain code; the model appears only where reasoning is required.
**Three questions to answer on purpose** (or the agent answers them with whatever was most common): where the model runs · how it is prompted (**"behaviour lives in the prompts, not the code — treat prompts as code, versioned and reviewed"**) · what happens when it is wrong.
**The fluent failure is the subtler danger** — a form check catches the malformed answer, never the well-formed wrong one. **So place a check outside the model.**
**Cost is architecture, not accounting.** Levers in order: choice of model (biggest) → prompt caching for the repeated part → parallelism for what does not depend. Then bound the **economic blast radius**: a hard cap on calls per run and on spend per run.

**M8 — design is specified, not drawn.** *"You need the eye, not the hand."* Norman's two gulfs — **execution** (what it asks) and **evaluation** (what it shows and admits). A spec must name four things: **user flow** (including the wrong paths — *"unnamed paths are where agents work worst"*), **information hierarchy**, **interaction model** (agents add controls no one wants), **feedback design** (*"an agent builds the happy path and stops"* — specify the slow, failed, and empty states). Right altitude: not colours and pixels, not "make it clean."
**A failure must look like failure** — a silent one shows a blank or a default, and *a default of "not guilty" enters the record.*
**Documentation:** from the beginning, not the end. **Descriptive** (the *what*) the agent writes well. **Explanatory** (the *why*) is rarely in the code — *"the agent cannot read it, so it invents."* Name the reader, the scope, and the length.

### 2.7 Verifying, in order

**M12 — version control is safety infrastructure.** Three demands: an agent changes more code than you can read; a spiral turn must be undoable; parallel agents must not collide. **The false choice:** slow the agent to a watchable pace (throwing away the speed) or accept its output unread (throwing away the engineering) — **safety infrastructure escapes the choice.**
- Git = the way back (mechanical). **Reading drift = the way of seeing (judgement, no tool supplies it).** *"Recovery makes mistakes survivable; seeing makes them rare."*
- **Harness checkpoints are not version control** — private, opaque, gone when the session ends.
- **Commit before any non-trivial task** (touches several files, or changes behaviour that works). Hand the committing to the agent; keep the judgement.
- **Atomic commits**; the diff shows *what*, so the message says **why** — *"would it still explain the change in six months?"*
- **Pre-commit hook as gate**: run tests, scan for secrets. **Never commit a secret** — history is permanent, it sits in every clone; treat any committed key as compromised and replace it. Enforce with a scanning hook, not with care.
- **Branch per agent, worktree per branch.** A merge conflict is ordinary; resolving it needs what neither agent had — **both intentions**.
- **Drift signals:** the agent rewrites code it wrote an hour ago; adds files and dependencies nobody asked for; explanations grow more elaborate; **tests pass but confirm the code, not the specification**; the plan keeps growing and no milestone arrives.
- **The "fix this bug" spiral:** each step small and reasonable, an hour later the code is worse. **The escape is not another correction but a stop** — return to the last good commit. *"Judge by the distance remaining, not the hours gone."* Drift is only visible as divergence from a session plan you wrote down first.

**M13 — every output is a hypothesis until checked.** *"'Finished' means it looks finished to the agent."*
- **Two questions, easily confused:** does the code do what the spec required (**build verification** — this module) versus does the model reason well (**product evaluation** — elsewhere).
- **Verification is a category, not a technique.** Five checks catch five different faults: *type check* (wrong kinds) · *test* (behaviour on a worked example) · *linter* (dangerous patterns, without running) · *review* (a design wrong as a whole). Worked example: a discounted-price function where the type check confirms numbers, a test confirms one example, the linter notices an ignored argument, and **only review sees the discount applied before the tax.**
- **Tests that confirm the implementation.** The agent writes the code, then reads its own code to write tests — so they check what the code *does*, never what the spec *wanted*. **A green suite proves the code agrees with itself.** *The median that enshrined its own bug:* sorts and takes the middle — right for odd lists, wrong for even — then records whatever its own function returned. Every test passes. **A later engineer who fixes it sees tests fail.**
- **Separate whoever writes the tests.** One agent codes, another writes tests **shown the specification and never the code**, so they cannot share the same hidden assumption. Neither survives a fault in the specification itself.
- **Verification theatre:** coverage records which lines *ran*, never whether anything was *checked*. **A trusted gate that catches nothing beats no gate** — for the worse.
- **No gate, no merge.** Define the gate before the work; a standard invented afterwards bends to fit the work. **"The day you skip it is the day it guarded."** Make the tools refuse the merge mechanically — *"willpower at a late hour is not a control."*
- **Cheap gates first, the human gate last.** Tests, type checks, linters need nobody present — run them early and often. Review and acceptance need a person, placed at the merge boundary. **Never spend attention on work the tests reject.**
- **Verify the code around the model:** check the shape before trusting, retry, fall back, or record failure. **Hand it broken responses on purpose and watch.**

**M16 — review is the central skill.** Almost all the code is code you did not write, so review is *"the one thing you do to everything."* **Two acts: reading** (an agent goes over every change — there is too much to read yourself) **and judging** (yours, because intent is yours). **"Whoever wrote the work cannot judge the work."** A tool review shares blind spots with the writer and cannot know what the project was for.

**The Merge-Readiness Pack — five criteria, each shown by evidence, never by claim:**
1. **Functional completeness** — it matches the specification
2. **Sound verification** — tests came *from the specification*
3. **Engineering hygiene** — it fits the project's standards
4. **Rationale** — someone wrote down *why*
5. **Auditability** — the whole trail can be followed

**Legacy code and decay.** *"Your own early code is legacy."* Three decay paths: **style drifts** (every session writes differently), **abstractions multiply** (the same helper written thrice), **reasons vanish** (they lived in deleted chats). *No single change was wrong, so nothing stopped it.*
**Four traps** in old code: assumptions age · **comments lie** (where code and comment disagree, the code is true) · tests certify the present, not correctness · dependencies move on.
**Refactor from the leaves upward.** Map the dependency graph, find the parts that depend on nothing, do those first and verify each, reach the root last. *"A wrong figure then points at one part; start at the top and five parts are suspect."* Tools: `/init` for the map, then **check the map against the code before trusting it**; `/code-review` (start without `--fix` and read the findings — *"a tool that fixes silently teaches you nothing"*).

### 2.8 Many agents

**M14 — a company of agents, not a cleverer one.** *"One agent is one worker with one context."* What many buy you: independent review as a **property of the system**; work too large for one context; parallel time compression; each part on the cheapest model that suffices.
- **Implicit orchestration** already happens — your tool divides work without telling you. **Explicit orchestration** is the arrangement you design: take over when the task exceeds the default division, to control cost, to run parts together, or when the work needs **roles the tool would never invent** (a security-only reviewer; an agent that attacks designs).
- **Restraint:** for an ordinary feature the tool's own division handles it. *"Orchestrating everything mistakes the machinery for the goal."*
- **Decomposition is the engineering.** A sound sub-task names **input, output, and the test showing the output is right**. Missing any of the three → send the split back. Independent sub-tasks may run together; a missed dependency feeds an agent a stale input; an imagined one makes you wait for nothing.
- **Show each agent only its own part.** **Never show an agent another agent's conclusion** — *"models continue the confident text they are shown. You wanted two views and got one, twice."*
- **N-version:** the same problem to several agents separately, none seeing another's work. Their differences show where the problem is genuinely hard. **Use different models, not different runs** — three judges on one model share that model's blind spot; **genuinely different models disagree, and the disagreement is the signal.**
- **Tokens are the constraint.** Each agent consumes its own; every handoff pays again for the context passed; **parallel work saves time but never saves tokens**; N versions cost close to N times. **Count the agents, contexts and versions on paper and estimate the multiple before spending.**

**M15 — the coordination design is an artefact.** Write the arrangement down before any agent runs: the patterns used and where; **each agent's role, input, output, and boundary**; how work passes; what happens when an agent fails; what the arrangement costs and buys. Proportionate — a few lines for three agents, a real document for a dozen. **Its value is being reviewable while correction is still free.**
- **Control lives at three levels:** the tool, your **code** (deterministic, same every time), and **prose instructions** (flexible, far less predictable). **Whatever must be reliable is enforced in code.**
- **Three rules:** *divide by definite rules* (one sub-task per file that must change; written as code it divides identically every run) · *watch the work while it runs* (workers report at defined points; dispatching code inspects reports; a worker touching files outside its boundary is caught) · *plan for the worker that fails* (retry, reassign to a stronger model, fall back, or escalate).
- **Interfaces are where your control is complete** — ordinary code and data contracts, no model unpredictability at that boundary — **and where arrangements most often fail.** **Structure lost at a handoff:** flatten a structured result to prose and the next agent must guess the parts back, sometimes wrongly and quietly. Defined boundaries are also the only thing you can log and inspect.
- **Cooperation is built, not requested.** Roles live in instructions; **handoffs live in your code.** *"Instructing agents to collaborate leaves you only hope."* Cross-reflection beats self-review, since agents defend themselves.

**The five workflow patterns:** **chaining** (ordered stages) · **routing** (a first agent sorts; simple goes cheap, hard goes capable) · **parallelization** (wait for the slowest, not the sum) · **orchestrator-workers** (the dividing itself is delegated; use when the division isn't known beforehand) · **evaluator-optimizer** (one produces, one judges, revise until good enough; for work where quality outweighs speed). Real arrangements combine several.

### 2.9 Security (M17)

**Security cannot be added later.** *"It is a thousand small choices while building… A system not built secure will not become secure."*
**The honest view versus the hostile view:** a screen fetches an order by the number given, every honest test passes — **but nobody checked that the order is theirs.**
**Why agent code fails more often:** an agent writes toward code that *works*, not code that *resists attack*. It omits the check, the limit, the stored key. **Studies find faults in near half of agent code** — the figures age, the direction does not.

- **Injection — data read as command.** Three faces, one fault: **SQL injection** (the query is subverted), **command injection** (the OS runs it), **cross-site scripting** (another user's browser runs it). One defence: **pass data through interfaces that cannot run it; never build a command by joining strings.** Agents write the unsafe version because joining strings is the shortest way that works and honest input never triggers the fault.
- **Secrets:** a key in the source travels with the source; the system works, so nothing warns you; attackers scan public code constantly. Keep keys outside and scan before every commit.
- **Dependencies:** **agents invent package names as readily as facts, and attackers register the names agents tend to invent.** Check each package exists and is intended; pin versions; review dependency changes as code.
- **Prompt injection — the fourth face, and the one your product has.** Your instructions and the user's text reach the model as one stream and **it cannot tell which has authority.** **"A charge sheet can order the judge to acquit."** No training has solved this inside the model. **Four defences, none complete alone:** mark untrusted input clearly as data · give the model the fewest powers possible · put a human where the stakes are high · gate the output before any real action.
- **When defences fail:** **least privilege** (with broad powers one breach breaks everything; with narrow powers the same breach reaches one table) and the **audit trail** (*"turns a mystery into a traceable case"*).
- **Tools:** `/security-review` on the diff, the security-guidance plugin on every edit, `security-patterns.yaml` for your own secret patterns. **Run these before the commit, never after.** *"A scanner finds patterns; it cannot find intent."*
- **Make the standard repeatable:** a **subagent file** (the reviewer's role and tools), a **skill** (your own slash command for the Merge-Readiness Pack), a **hook** (fires without being asked). **Write the five criteria into the reviewer's instructions.**

### 2.10 Working conditions (L7, before Module 12)

Csíkszentmihályi's **flow** — immediate feedback, skill–challenge balance, intrinsic motivation, time distortion, merged attention. **Recovery from an interruption takes 30 minutes.**
**Agentic coding vs flow — pro:** fewer distractions, almost immediate feedback. **Contra:** you must respond to agent requests, which breaks concentration; the role turns passive; the "fix this bug" spiral.
**His remedies:** force an **active** mental model — write a session plan and check it every 30 minutes; work in 90-minute intervals; reduce notifications; run several sessions in parallel; use teams of agents.

**The project pipeline he gives (L7 s4 / L8 s5):**
> idea → description → design (produces a set of files) → specs → `CLAUDE.md` / `AGENTS.md` → ask the AI which skills and MCPs you need → start prompting, and give it the design files.

---

## 3. Cross-lesson connections

**Blast radius appears in four currencies.** Physical (L2 M3 — tools define reach) → procedural (L3 M5 — sandboxing, question 3) → **economic** (L5 M9 — cap calls and spend per run) → **security** (L9 M17 — least privilege: "with narrow powers, the same breach reaches one table"). Same idea each time: *bound what a single mistake can touch.*

**The gate recurs at every scale.** L1 names it as one of four verification instruments → L3 makes audit-trail mechanisms an ADE question → L7 M12 makes it a **pre-commit hook** → L7 M13 makes it the merge boundary (*no gate, no merge*) → L9 M16 gives it a settled form (**the Merge-Readiness Pack**) → L9 M17 makes it repeatable as a subagent + skill + hook. The final lesson closes the loop opened in the first.

**"Whoever wrote the work cannot judge the work"** is one principle in three modules. M5 (multi-agent coordination — *"a single agent approving itself has no real check"*), M13 (separate whoever writes the tests, and show them the spec, never the code), M16 (the two acts, reading and judging). M14 sharpens it further: independence must come from **different models**, not different runs, or the blind spot is shared.

**Silent failure is the through-line of everything that goes wrong.** The agent fills context gaps silently (M3) → a thin spec is filled with hidden assumptions (M4) → a silent UI failure enters a default verdict into the record (M8) → a green test suite proves the code agrees with itself (M13) → wrong context "makes no complaint" (M11) → decay happens because "no single change was wrong, so nothing stopped it" (M16) → the injection fault never fires on honest input (M17). Every discipline in the course is a way of **making a silent failure loud.**

**Two things named as what separates engineering from craft**, and they are the same thing: the **audit trail** (M4) and the **structure around the work** (M1's password-reset comparison — *"the second did not write cleverer prompts; they built a structure around the work"*). His grading policy — *only what I can open and verify in your repo counts* — is the course's own thesis applied to the students.

**What the specification does grows over the course.** M4: it fixes decisions the agent won't guess. M10: it becomes the primary artefact you version and regenerate code from. M13: it becomes the *source* the test-writing agent is shown instead of the code. M16: "tests came from the specification" is criterion 2 of the merge gate. By the end, the specification is the thing under version control and the code is downstream of it.

**A gap to note:** Part 4 of the course (Modules 18–19 — entrepreneurship in the age of AI coding, and where software work is going) is previewed in Lesson 1 but not covered in these nine decks.

---

## 4. 🏛️ The Tribunal — the running project

Introduced **Lesson 4, slide 27** (a diagram with no text layer), then used as the worked example in nearly every module through Lesson 9.

### 4.1 What it is

> **"It weighs one hard decision from many sides, and never hands you a single answer."**

| Piece | Definition (verbatim) |
|---|---|
| **Charge sheet** | "One decision, put as a precise question." |
| **Four advocates** | "Two argue for, two argue against." |
| **Three judges** | "Each rules alone, and gives reasons." |
| **A protocol** | "Three verdicts, kept side by side." |
| **You decide** | "You weigh them and judge for yourself." |

**The canonical case:** *Jon Snow kills Daenerys, and the panel is asked whether it was justified.* Defence: Jon Snow and Tyrion. Prosecution: Daenerys and Grey Worm. Verdicts render as *justified / not justified / not justified.*

> **"The Tribunal never merges the verdicts. You stay the judge."**

The design mirrors the course's own doctrine: independent judgement, disagreement preserved rather than averaged, and the human left holding the decision.

### 4.2 The build assignment (L4 s45)

- A **browser screen** with the three-part charge sheet — **the defendant, the act, the exact question** — and the opinion shown back.
- A **backend** that checks the sheet, calls the model, and **keeps the key on the server**.
- A **database** that stores every charge sheet and its opinion, so a past case can be found.

Per-layer detail (L4 s35–41): the backend holds **the OpenRouter key, the rubric, and the prompts**. The database keeps charge sheets, opinions, and **a log of every model call — the model, the verdict, the tokens, the cost, the time**. Deployment is what lets someone else open it at a web address and put a case. Open question posed to the class: **SQL or NoSQL, and what makes you lean that way?**

### 4.3 Interface requirements (L5, M8)

Stated problem: **"The Tribunal's opinion arrives as one undivided block."** Fixing that is the module's work.
- **Hierarchy:** the verdict first, reasons after, fuller arguments below. **Show the three verdicts together, not buried** — otherwise the disagreement has to be pieced together, and the disagreement is the point.
- **Interaction:** only a form and a button. Resist the controls the agent will add.
- **Flow must cover the wrong paths:** an incomplete charge sheet; what shows while the panel is deliberating.
- **Failure:** a silent failure defaulting to *not guilty* would enter the record and be believed. **Show failure as failure, never as verdict.**

### 4.4 Economics (L5, M9) — the numbers to design against

- **One deliberation is seven calls, not one** (4 advocates + 3 judges).
- **Each judge reads all four advocate arguments** — which is why the judges cost most.
- **A full case spends roughly 17,000 tokens.**
- **Cost grows faster than the agent count.**
- **Caching:** all seven agents read the same charge sheet — cache the shared part, pay once.
- **Latency:** seven sequential calls ≈ **21 seconds**; the four advocates are independent and run at once, so run well it's ≈ **6 seconds**. The judges must wait for the advocates.
- **Cap it:** a hard limit on calls per deliberation and on spend per run.
- **Variability is the component's nature:** "give one charge sheet twice to a judge — it may rest the verdict on different grounds, and on a hard case it may even flip." **So you cannot test one fixed answer.**

### 4.5 Specification hooks (L6, M10)

- Part 1 standard: **"show reasoned disagreement."**
- Part 2 criterion: **"require a verdict plus at least two reasons"** — the countable replacement for "well reasoned."
- Part 5 pitfalls named for it: a judge may return **prose** instead of the fixed form; a model call may **time out**; a charge sheet may **lack its question**.
- The rule that answers the first (L6 s62): **"Demand the fixed form twice."**

### 4.6 Multi-agent design (L8, M14–15)

The Tribunal *is* the multi-agent module's example. **"Three judges on one model tend to agree — they share whatever blind spot that model brought. Genuinely different models disagree, and disagreement is the signal."** Hence: **"The panel reaches seven distinct models this way."**

That makes the Tribunal an **N-version** arrangement (the same question to independent agents, none seeing another's work) combined with **parallelization** (the four advocates at once) and **chaining** (advocates → judges). It also inherits M14's hard rule: **never show one agent another's conclusion** — *"you wanted two views and got one, twice."*

### 4.7 Legacy and security (L9, M16–17)

- **"Your own early code is legacy" (M16, s14):** *"The Tribunal's first sketch predates every discipline taught here. It assumes one model call for one deliberation. Its comments describe what the sketch meant to do."* → **judge your past work as if it were foreign; give it none of the credit memory offers.**
- **Prompt injection is the Tribunal's own vulnerability (M17, s40):** **"A charge sheet can order the judge to acquit."** The charge sheet is user text going straight into a model prompt — the four defences (mark input as data, fewest powers, human at high stakes, gate the output) apply directly.

### 4.8 Checklist for the build

| From | Requirement |
|---|---|
| M6 | Problem statement, stakeholder list, definition of done, out-of-scope list |
| M7 | Four layers; key on the server; a per-call log (model, verdict, tokens, cost, time) |
| M8 | Verdict-first hierarchy; three verdicts side by side; slow/failed/empty states specified |
| M9 | Parallel advocates; prompt caching; per-model choice; hard cap on calls and spend |
| M10 | A versioned five-part spec; "verdict plus at least two reasons"; pitfalls written down |
| M11 | A `CLAUDE.md` under 200 lines, hand-written, rules at the edges |
| M12 | Atomic commits saying *why*; pre-commit hook scanning for the OpenRouter key |
| M13 | Tests written from the spec by an agent that never saw the code; shape-check the model output; no gate, no merge |
| M15 | A written coordination design: each agent's role, input, output, boundary, failure plan |
| M16 | The Merge-Readiness Pack, evidenced not asserted |
| M17 | Charge sheet marked as data; least privilege; output gated before any action |

---

## 5. Glossary

**ADE** — agentic development environment; the tool you direct the agent through. Judged on six pillars, three surfaces, seven questions.
**Agent** — model + tools + loop.
**Atomic commit** — exactly one logical change, so it can be undone alone.
**Audit trail** — the record of decisions, actions and verification; part 7 of the turn; *"most sharply separates engineering from craft."*
**Blast radius** — how far a mistake reaches. Physical, procedural, economic, and security forms.
**Build verification vs product evaluation** — does the code match the spec, versus does the model reason well. Different questions, different modules.
**Calibrated trust** — trust proportioned to observed competence, because the frontier is jagged.
**Cognified software** — software whose core behaviour is carried by a model at runtime (Kevin Kelly, 2016).
**Co-evolution spiral** — problem and solution reshaped together across repeated turns (Dorst & Cross 2001; Boehm 1988).
**Compaction** — the conversation replaced by a structured summary as the window fills. Chat instructions vanish; files return.
**Context engineering** — deciding what occupies the window, treated as a budget rather than a container.
**Context window** — everything the agent knows this session. It cannot tell what it is missing.
**Cross-reflection** — a second agent examines the first's work; stronger than self-review, since agents defend themselves.
**Decomposition** — dividing one task into sub-tasks that each name input, output, and test.
**Drift** — a session diverging from its plan; visible only if the plan was written first.
**Equipping** — the five things supplied before the agent acts: right problem, specification, context, tools & permissions, limits.
**Erode / hold / compound** — the three fates of a skill as agents improve.
**Evaluator-optimizer** — one agent produces, a second judges, the first revises.
**Explicit vs implicit orchestration** — the arrangement you design, versus the one your tool already runs beneath the surface.
**Fluent failure** — a well-formed, confident, wrong answer. A form check cannot catch it.
**Gate** — the barrier work must pass to enter the project. *No gate, no merge.*
**Hook** — code that fires on a tool event and can refuse the action. *A file advises; a hook enforces.*
**Jagged frontier** — competence following training data rather than human notions of difficulty.
**Merge-Readiness Pack** — functional completeness, sound verification, engineering hygiene, rationale, auditability. Each shown by evidence.
**N-version** — the same problem given to several agents independently; their differences mark genuine difficulty.
**Prompt injection** — untrusted input read by the model as instruction. *"A charge sheet can order the judge to acquit."*
**Reverse interview** — the agent interviews you; the prize is its list of assumptions where you said nothing.
**Seven parts** — intent, specification, context, plan, execution, verification, audit trail.
**Tacit knowledge** — what cannot be fully written down (Polanyi, 1966). The boundary of what erodes.
**Verification theatre** — a gate that runs and catches nothing; coverage without checking.
**Vibe coding** — Karpathy, 02.02.2025. *"Useful as a label and misleading as a guide."*
