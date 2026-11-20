# Live Football World Cup Scoreboard

A small Java 21 library that keeps a live scoreboard of several simultaneous matches. It is a library, not an
application: no `main`, no HTTP layer, no runtime dependencies (JDK only).

```
./mvnw test        # build and run all tests (needs JDK 21; the Maven Wrapper fetches Maven)
```

## Usage
Example from brief com.odds.scoreboard.InMemoryScoreboardTest.GetSummary.referenceScenarioFromTheBrief
```java
Scoreboard board = new InMemoryScoreboard();
board.addListener(event -> System.out.println(event));       // optional: react instead of polling

board.startMatch("Mexico", "Canada");                         // R3: 0-0, added to the board
board.startMatch("Spain", "Brazil");
board.updateScore("Spain", "Brazil",                          // R4: record one scoring action
        Score.goal("Spain", 10, "Pedri", Duration.ofMinutes(12), "g1"));

board.getSummary();                                           // R6: Spain 1 - Brazil 0, Mexico 0 - Canada 0
board.finishMatch("Spain", "Brazil");                         // R5: removed from the board
board.getPlayerScore("Spain", 10, "Pedri").points();          // R7: 1, still found after the match finished
```

| Brief | API |
|---|---|
| R3 Start a match | `Match startMatch(home, away)` |
| R4 Update the score | `Match updateScore(home, away, Score)`; a `Score` is team, shirt number, surname, points, elapsed time, `scoreId` |
| Correction | `Match removeLastScore(home, away)` removes the last score of a live match, for example a disallowed goal |
| R5 Finish a match | `Match finishMatch(home, away)` returns the final result |
| R6 Summary | `List<Match> getSummary()`: total score descending, then most recently started first |
| R7 Player lookup | `PlayerScore getPlayerScore(team, shirtNumber, surname)`: tournament-wide, grouped per match |
| Delivery channel | `void addListener(ScoreboardListener)`; events `MatchStarted`, `ScoreRecorded`, `ScoreRemoved`, `MatchFinished` |

`Match`, `Score` and `PlayerScore` are immutable records. Only the API types are public; everything else is
package-private.

## Behaviour in short

- **Identity.** A live match is its `(home, away)` pair; order matters. A team plays at most one live match at a
  time. The same pair may play again after finishing (a new match).
- **Names.** Opaque and case-sensitive, stripped of surrounding whitespace. `null` throws `NullPointerException`,
  blank or invalid values throw `IllegalArgumentException`.
- **Errors.** Unknown or finished match: `MatchNotFoundException`. Team already playing:
  `TeamAlreadyPlayingException`. No points for a player: `PlayerNotFoundException`. No score to remove:
  `NoScoreToRemoveException`. A rejected call never changes the board.
- **Scores.** `updateScore` records one scoring action and adds its points, so a score has a scorer, a time and a
  value. `scoreId` is caller-supplied and unique within a match. Replaying an identical score is a no-op (current
  match returned, no event). Reusing an id with different content throws `IllegalArgumentException`.
- **Scorers.** `Match.scores()` is chronological by match time; equal times keep arrival order.
- **Removing a score.** `removeLastScore` removes the last element of that list and lowers the total. A removed
  `scoreId` can be recorded again as a new score. Only live matches can be changed; finished matches keep their scores.
- **Player lookup.** A player is the exact triple (team, shirt number, surname), where team is the team credited
  with the points. The result lists, per match played, the opponent and the player's scores.
- **Listener.** Events are published once per successful change, synchronously on the caller's thread, in the order
  the state changed. A listener that throws is logged and isolated; delivery is best-effort.
- **Thread safety.** All public methods are safe to call from several threads.

## Assumptions

The full numbered list (A1 to A99, with the reasoning) is in [NOTES.md](NOTES.md); later entries supersede earlier
ones. The ones that shape the design:

- Single JVM, in-memory, one board per tournament. State is lost on restart.
- Team names are canonical identifiers supplied by the caller; no roster, fixture or squad validation. Shirt numbers
  are 0 to 99 and stable for the tournament.
- Match time is the elapsed time since kick-off, supplied by the caller (no upper bound, no periods). Points are at
  least 1 with no sport-specific rules, so the same model fits basketball.
- Scores go up with `updateScore` and can only be taken back one at a time, last first, with `removeLastScore`.
  There is no removal by id.
- A Kafka listener is out of scope. The in-process `ScoreboardListener` is the seam where a producer would plug in.

## Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| Interface `Scoreboard` plus `final InMemoryScoreboard` | Consumers can stub the interface in their own tests | One extra type |
| Match identity is the `(home, away)` pair | Feeds already have the pair; no ID bookkeeping | Names double as identity; a `MatchId` returned from `startMatch` is the alternative |
| Immutable `Match` snapshot with enforced invariants | Callers can't see or build an inconsistent match; safe to share across threads | Each update re-validates the snapshot in O(k) scores |
| `updateScore` records an action instead of setting a total | Captures who scored, when, and supports multi-point sports | Not naturally idempotent, hence `scoreId` |
| `scoreId` instead of value-based de-duplication | Two identical basketball free throws at the same clock time are legitimate | Callers must supply ids; it also leaves room for a future `cancelScore(scoreId)` |
| Start order from insertion order, not a wall clock | Timestamps collide within a millisecond and give flaky order | None for this scope |
| One `ReentrantLock` over all state | Start is a check-then-act over all entries and the summary needs a consistent view, which `ConcurrentHashMap` alone can't give. `synchronized` pins virtual threads on JDK 21 | Writers serialise; irrelevant at this scale |
| Listener, not an extra operation | Consumers react instead of polling; adds no new input or output | Synchronous delivery inside the lock: a slow listener slows writers |
| Summary sorted on every read | At most a handful of live matches, so O(n log n) is negligible; no second structure to keep consistent | Revisit only if n grows by orders of magnitude (a cached list was considered and declined) |
| Finished matches kept in a list for the lookup | The lookup spans the tournament | Memory grows with one tournament, bounded because one board is one tournament |
| `removeLastScore` takes the chronologically last score, not the last one received | A disallowed goal is normally the latest event, and `Match` needs no extra state to track arrival order | After a late-arriving earlier score, "last" is not the one received last; removal by `scoreId` is the follow-up |
| Lookup groups scores per match | A flat list can't show which match a score came from | Slightly richer result type |
| Unchecked exceptions | Invalid calls are caller or data errors and must surface | Callers who prefer `Optional` or result types must wrap |
| JDK only, no logging framework | A library shouldn't impose a dependency graph | Listener failures go through `System.Logger` |

## Known limitations and follow-ups

- No `cancelScore(scoreId)` to remove an arbitrary score (only the last one can be removed), no `removeListener`,
  no own-goal flag.
- Football notation such as 45+2' becomes 47:00. A period-aware match clock would fix that.
- Basketball shirt `00` can't be told apart from `0`.
- `getPlayerScore` scans the retained scores (about 170 goals in a World Cup). An index is the follow-up if profiling
  ever asks for it.
- Rename or typo corrections of a team mid-match aren't supported, because names are identity.
- No JPMS `module-info.java`: package-private visibility already hides the internals.
- Multi-tournament use means one board instance per tournament.

## Tests

JUnit 5 and AssertJ, no mocking library (hand-written fakes). 159 tests cover validation, snapshot invariants, the
operations and their error cases, ordering, replay and conflict handling, removing the last score, event publishing,
the player lookup, and N-thread races. In those, 32 threads are released together by a latch and the tests check
that there is one winner per start, finish or removal, that no points are lost when updates, removals and a finish
race, and that listeners receive events in exactly the order the state changed. Temporarily removing the lock made
the concurrency tests fail.

`-Xlint:all` is on and the build is warning-free.

## Layout

```
src/main/java/com/odds/scoreboard   Scoreboard, InMemoryScoreboard, Match, Score, PlayerScore, ScoreboardEvent,
                                    ScoreboardListener, exceptions; Names and PlayerKey are package-private
src/test/java/com/odds/scoreboard   one test class per type, plus concurrency and listener tests
NOTES.md                            task, numbered assumptions, plan
AI.md                               how AI was used
```
