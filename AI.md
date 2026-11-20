# AI usage

## Summary

The implementation was built with **Claude Code** (CLI) running **Claude Sonnet 5.5**. The AI wrote the project
skeleton, the production code and the tests, one small step at a time. A human supplied the task brief and the
design assumptions, decided every open question, and reviewed each step before the next one started. Nothing was
committed while the library was built; the git history was created afterwards, on request (see
[Commit history](#commit-history)).

## How the work was organised

Five custom Claude Code skills (in `~/.claude/skills`, outside this repository) drove the workflow:

| Skill | What it did here |
|---|---|
| `/java-init plain maven` | Generated `CLAUDE.md` (stack, commands, engineering rules) from a template |
| `/java-new` | Set up the Maven skeleton (JUnit 5, AssertJ, wrapper) on top of an existing IntelliJ stub |
| `/plan-check` | Proposed a 9-step plan, assumptions and open questions from `NOTES.md` |
| `/slice` | Built exactly one plan step: state it, write tests first, minimum code, run the full suite, tick the step, report and stop |
| `/edge-cases` | Read-only review: listed uncovered edge cases against the code, tests and notes, and asked which to test |

Each `/slice` step followed the same loop: the tests were written first and seen failing, then the code was added,
then the whole suite was run, then the step was ticked in `NOTES.md`.

## Prompt history (condensed and paraphrased, in order)

1. `/java-init` with no arguments: refused and printed usage. Then `/java-init plain maven`.
2. `/java-new`: stopped because the folder already had a pom and `src/`. Chose to keep the stub and add the missing
   pieces.
3. `/plan-check`: got a proposed plan and three questions. The answers: only the in-process listener (no real Kafka),
   a caller-supplied `scoreId` on `Score` (spec D18), base package `com.odds.scoreboard`, and per-match grouping in
   `PlayerScore`. The plan and assumptions A85 to A93 were then written to `NOTES.md`. A93 (the `cancelScore` hook)
   was added on request.
4. `/slice` repeated for steps 1 to 9. Asked for two refactors of `Match` (a stream for the
   chronological insert, then a `require` helper plus stream totals to cut `if`s).
5. Asked whether the summary sort could be optimised; the options were explained and the human declined.
6. Asked how to try the library with custom input; answered with a `jshell` session on the compiled classes.
7. `/slice` for step 10: this README and this file.
8. Asked for an extra plan step to remove the last score. Two plan steps (the feature, then the docs) and
   assumptions A94 to A99 were added to `NOTES.md`.
9. `/slice` for step 11 (`removeLastScore`, built tests first) and step 12 (updating the README, this file and the
   notes).
10. `/edge-cases` (read-only): listed 11 edge cases with their coverage and a top 3. The human chose rows 1 and 2
    (races on removal, update and finish, and listener event order), which became 5 new concurrency tests.

## Who decided what

- **Human:** the task brief, assumptions A1 to A84 (already in `NOTES.md` before the implementation session),
  the in-process-only listener, package name, the per-match `PlayerScore` result, declining the summary
  optimisation, choosing which edge cases to test, and approval of each step. Some manual production code and tests, mostly refactoring.
- **AI:** the plan (`/plan-check`), assumptions A85 to A99 (approved by the human), most production code and tests,
  the `CLAUDE.md` text from the template, and the commit-message suggestions.

## Verification

- Tests first, seen failing before the code existed, then the full suite after every step (159 tests at the end).
- The concurrency tests were checked by temporarily disabling the lock: 3 or 4 of the original 5 failed on each of
  three runs. After the 5 later tests were added, 4 of them failed on every run without the lock. The fifth, the
  finish race, did not fail, so it was strengthened to repeat over 100 fresh boards and then failed on 3 of 3 runs
  without the lock. The file was restored from a backup each time.
- The README usage example was run in `jshell` against the compiled classes.
- Corrections along the way: a compile error in `PlayerScore` (a lambda reading parameters that the constructor
  reassigns) was fixed before the step was reported, and the weak finish-race test described above was strengthened.

## Commit history

The library was finished before anything was committed. At the human's request, Claude Code (running Claude
Opus 5.5) then split the finished code into the plan's steps, one commit per step, with the docs last. Each commit
contains only code from the finished files, with later features left out, and each was checked to compile and pass
its tests before the next one was made. Commit dates show when this history was created, not when each step was
written. Commits after the docs commit were made as that work happened.

## Artifacts that guided the implementation

- `NOTES.md`: the task, the numbered assumptions (A1 to A99, later entries supersede earlier ones) and the plan.
- `CLAUDE.md`: the stack, commands and rules (small steps, tests with every change, no new dependencies without
  asking, thread safety, no swallowed exceptions, `-Xlint:all` clean).
- The five skills above.

*The prompt history is paraphrased, not a verbatim transcript. Review both documents before submitting.*
