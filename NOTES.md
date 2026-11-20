# Task
Problem statement

Implement a library that manages a live football World Cup scoreboard holding multiple simultaneous matches.

Functional requirements
R3	Start a new match. Home team vs away team, initial score 0–0, added to the board.
R4	Update the score of a match in progress. Realised as recording one scoring action (Score: team, shirt number, surname, points, elapsed match time). The match keeps both the summarised score and the list of individual scores (A61–A72).
R5	Finish a match in progress. It's removed from the board.
R6	Summary of matches in progress, ordered by total score descending. Equal totals: most recently started first.
R7	GetPlayerScore(team, shirtNumber, playerSurname), a tournament-wide player lookup (A73–A84). 
The listener is a delivery channel, not an operation (A40).

Delivery and non-functional requirements
R1	A simple Java package in a Maven project. A library, not an application.
R2	A git repository at code-review quality, following my own engineering standards.
R8	README.md covers assumptions, reasoning, trade-offs.
R9	AI.md covers a short summary of AI usage, prompt history and context, and any artifact that guided the implementation.

# Assumptions
Scope and packaging
A1. Single-JVM, in-memory library. No persistence, HTTP layer, CLI or main. Why: the brief asks for a simple library; consumers wrap it as they need. Cost: state is lost on restart, and that's acceptable for this scope. (D1)
A2. No runtime dependencies. Only the JDK. No Spring/DI, no Lombok, no logging framework. Why: a library shouldn't impose a dependency graph on its consumers. Records replace Lombok.
A3. Java 21 LTS, Maven 3.9+ via Maven Wrapper. Why: records, sealed types and pattern matching keep the model small. The wrapper means reviewers build without installing Maven.
A4. One instance is one board. Several tournaments mean several instances. No tenant or tournament key. This is also what bounds memory now that finished matches are retained (A84).
A5. Coordinates com.example:scoreboard, package com.example.scoreboard are placeholders (C4). Replace them before the first code commit.
A6. One flat package. Only API types are public. No JPMS module-info.java. Why: package-private visibility already hides internals. JPMS adds Surefire/module-path friction for no gain in a kata. Cost: mention JPMS as a possible follow-up.
Domain: teams
A7. Team names are opaque, case-sensitive identifiers. The caller supplies canonical names. Why: in production, teams come from a reference-data service with IDs. Guessing equivalence (case, locale, diacritics, Turkish İ) is error-prone. Cost: "Spain" and "spain" are different teams, which is documented. (D4)
A8. Names are normalised with String.strip(). It's Unicode-aware, unlike trim(). Null throws NPE; blank after stripping throws IAE.
A9. One normalisation function (package-private Names.normalize(value, field)) is used by Match, Score and the lookup key, so start, update and finish can never disagree about a name.
A10. Home ≠ away, compared after normalisation.
A11. A team is in at most one live match at a time, as home or away.
A12. No fixture or registry validation. Any two distinct names can play.
Domain: matches and identity
A13. A live match is identified by its (home, away) pair. Order matters. (away, home) is a different, non-existent key, so it's not found. Why: given A11, the pair is a natural unique key, and callers (feeds) already have it. No surrogate-ID bookkeeping. Cost: names double as identity, so renaming a team mid-match isn't supported. The alternative is a MatchId returned from startMatch; list it in the README trade-offs. (D2)
A14. Match is an immutable snapshot (record) with value semantics: teams, summarised score and the list of individual scores. Equality is by value, not identity. Identity lives in the board (MatchKey), not in Match.
A15. The same pairing may play again after finishing (group stage, then knockout). The rematch is a new match with a new start position.
Domain: summarised score

Redesigned: updateScore now records individual scores (next section). A16–A19 keep their numbers so references stay valid.

A16. updateScore records one scoring action and adds its points; it no longer sets absolute values. Why: it captures who scored, when and for how much, and supports multi-point sports. Cost: updates are no longer idempotent, so a replayed feed message double-counts. The mitigation is C5 (scoreId). (D3)
A17. Scores can only go up. There's no way to lower a score (e.g. a VAR-disallowed goal) without a cancel operation, and that would be a second extra operation, which the brief forbids. It's documented as a follow-up; C5 makes it possible later. (Superseded by A94–A99: removeLastScore exists; removal by id is still a follow-up.)
A18. Totals are sums of positive int points. Overflow isn't a realistic concern.
A19. There's no "no-op update" any more. Every accepted Score changes the match. With C5, a replayed scoreId becomes the no-op case.
Domain: individual scores (A61–A72, added in the redesign)
A61. Score is an immutable record: team, shirtNumber, playerSurname, points, matchTime. Score.goal(...) is the football shortcut with points = 1.
A62. team is the team credited with the points, given by name, and it must be one side of the match; otherwise IAE. Alternative: enum Side { HOME, AWAY }, which is type-safe but makes each Score less self-describing in lists and events. (D14)
A63. shirtNumber is 0–99. Known limit: basketball "00" can't be told apart from "0".
A64. playerSurname is non-blank and stripped. For mononymous players (Pelé, Neymar) it holds the known name. Players with the same surname are told apart by shirt number.
A65. No roster validation. The library doesn't know squads. For an own goal, team is the side credited and the player is whoever the feed reports. An own-goal flag is a follow-up. (D17)
A66. points >= 1, with no sport-specific rules (football 1; basketball 1/2/3). Validating legal values is the feed's job, or a future ScoringRules policy's. (D15)
A67. matchTime is a Duration of elapsed match time since kick-off, supplied by the caller. It must be non-null and non-negative, with no upper bound (stoppage, extra time, overtime). Cost: football notation such as 45+2' becomes 47:00, the same value as a second-half 47'. A period-aware MatchClock is a follow-up. (D16)
A68. The scorer list is chronological by matchTime, and equal times keep arrival order (stable insertion). Out-of-order arrivals are accepted and inserted in place, because rejecting late feed data would lose information.
A69. Match stores both views: homeScore/awayScore as record components (O(1) reads for summary sorting) and List<Score> scores. A record can't hold extra fields, and its canonical constructor is always public, so the compact constructor enforces the invariant:
every score belongs to one of the two sides
the list is ordered by matchTime
each total equals the sum of that side's points
the list is copied with List.copyOf
Cost: O(k) per snapshot, which is negligible (football < 10 scores, basketball ~150). (D13)
A70. Match.start(...) and Match.withScore(...) are package-private. Consumers only ever get snapshots from the board.
A71. updateScore is a read-modify-write (current snapshot, then withScore, then put). Unlike the old absolute set, a race here loses points. That raises the stakes for the lock (A33–A35), and it gets its own concurrency test.
A72. Value-based de-duplication is deliberately not done. Two basketball free throws by the same player at the same stopped clock are identical but legitimate. Only an explicit scoreId (C5) can de-duplicate correctly.
Ordering
A20. Primary key: total score, descending. Secondary key: start recency, descending. Recency comes from a monotonic long sequence assigned on each successful start, not from a wall clock. Why: timestamps collide within a millisecond, which gives nondeterministic order and flaky tests. A sequence needs no Clock. (D5)
A21. Sequence numbers are unique, so the order is total and the summary is fully deterministic. No third tie-break is needed.
A22. Score updates don't change start recency.
A23. Only successful starts get a sequence number. Gaps would be harmless anyway; ordering only needs monotonicity.
Lifecycle
A24. finishMatch removes the match from the live board. From S9b it moves to an internal finished store, used only by the player lookup (A75). Finished matches can't be queried directly. (D6)
A25. Any operation on an unknown or finished match throws MatchNotFoundException. That includes finishing the same match twice.
A26. "Live" is the only state. No scheduled, half-time, paused or abandoned states.
API contract and error handling
A27. Fail fast with unchecked exceptions.
NPE for null arguments (Objects.requireNonNull).
IAE for invalid values: blank name, same team twice, a Score with points < 1, a shirt number outside 0–99, a negative match time, or a team that isn't in the match.
ScoreboardException subclasses for state violations: MatchNotFoundException, TeamAlreadyPlayingException.
Why: invalid calls are caller or data errors and must surface. Unchecked exceptions keep the API clean, and the specific types let callers react precisely. Cost: callers who prefer Optional or result types must wrap. (D7)
A28. Rejected operations leave the state unchanged. Validate first, then mutate. There are no partial updates.
A29. Mutators return the resulting Match. finishMatch returns the final result. (D11)
A30. getSummary() returns an unmodifiable snapshot (Stream.toList()) of immutable records. It's never a live view. (D10)
A31. Exception messages name the offending values, e.g. Team 'Spain' is already playing in Spain 1 - Brazil 0. Custom exceptions declare serialVersionUID, which -Xlint:all requires to stay clean.
A32. Public interface Scoreboard plus final class InMemoryScoreboard. Why: it's a library, so consumers depend on and stub the interface in their own tests. (D12)
Concurrency
A33. Thread safety is part of the contract (C2). Feed consumers may call the board from several threads.
A34. One ReentrantLock guards all state, and every public method takes it. Coarse-grained locking is the simplest correct option, and contention is irrelevant at this scale. (D9)
A35. Why ConcurrentHashMap alone isn't enough:
startMatch must check that neither team is playing and then insert. That's a check-then-act across all entries, which per-key atomicity doesn't cover.
getSummary() needs a consistent snapshot across entries.
A36. Why ReentrantLock over synchronized: on JDK 21, synchronized pins virtual threads to their carrier (JEP 491 fixes this only in JDK 24). That matters once listener callbacks run under the lock (A44).
A37. No ReadWriteLock and no fairness. There's no measured need, and they add complexity.
Performance
A38. n (live matches) is tiny. A World Cup has at most a handful of matches at once.
startMatch: O(n), because it scans for team availability.
updateScore and finishMatch: O(1).
getSummary: O(n log n).
Sort on read rather than maintaining a sorted index. Cost: revisit only if n grows by orders of magnitude. (D8)
A39. Memory is bounded by the matches of one tournament. Finished matches are retained for player lookups (A75), and one instance serves one tournament (A4).
Change notifications: score-change listener (not the extra operation)
A40. The listener is a delivery channel, not the brief's extra operation. It adds no new input or output; it pushes the results of start, update and finish instead of making callers poll. The extra operation is the player lookup (A73). README states this framing, and the listener lands in its own commit. The API is void addListener(ScoreboardListener). Events form a sealed hierarchy of records: MatchStarted(match), ScoreRecorded(match, score) (the resulting match plus the exact Score recorded), and MatchFinished(finalResult) (the final score and the full scorer list). (A96 adds ScoreRemoved(match, score).)
A41. Why a listener: Sportradar pushes real-time data to its consumers (odds engines, widgets, notifications). Consumers need to react to changes, not poll getSummary(). The listener is the in-process seam where a production adapter, such as a Kafka producer, would plug in. Because events carry the individual Score, consumers get scorer and timing data without diffing snapshots.
A42. Events are published only after a successful state change, exactly one per operation. Rejected operations publish nothing. ScoreRecorded.score isn't necessarily the last element of match.scores(), because events follow arrival order while the list is chronological (A68). With C5, an ignored replay publishes nothing.
A43. Delivery is synchronous, on the caller's thread, inside the lock. Every listener sees events in exactly the order the state changed. Cost: a slow listener slows writers. Production would hand events off to a queue or async dispatcher.
A44. Listeners must be fast and non-blocking, and must not call mutating methods. This is a Javadoc contract and isn't enforced.
A45. A listener that throws a RuntimeException is isolated: the exception is caught and logged via System.Logger at WARNING, and the other listeners are still notified. The operation still succeeds. Delivery is best-effort, at-most-once. Errors are not caught.
A46. Listeners are stored in a CopyOnWriteArrayList. Registration is rare and iteration is frequent. A null listener throws NPE.
A47. No removeListener; registration only, to keep the surface minimal. It's a documented follow-up.
Extra operation (R7): player score lookup (A73–A84, added in the redesign)
A73. The brief's one extra operation is PlayerScore getPlayerScore(String team, int shirtNumber, String playerSurname). It lands in its own commit (S9b). (D19)
A74. A player's identity is the triple (team, shirtNumber, playerSurname), and all three must match exactly (after strip(), case-sensitive). Same team and number with another surname, same team and surname with another number, or the same number and surname for another team: all different players. The triple's validation lives once, in a package-private PlayerKey shared by Score, PlayerScore and the lookup. (D24)
A75. Scope is the whole tournament: live and finished matches of this board instance. From S9b, finishMatch moves the entry into an internal finishedMatches list, which isn't exposed through getSummary() or anything else. (D6, D20)
A76. The triple is stable for the tournament. World Cup squad numbers are fixed for the whole tournament. In competitions where numbers change, player IDs from reference data are the fix (follow-up).
A77. team means the team credited (Score.team, A62/A65), so an own goal counts under the credited side, not under the scorer's own team.
A78. The result is PlayerScore(team, shirtNumber, playerSurname, List<Score> scores) with a derived points(). Scores are in tournament order: match start order, then match time. Match totals are read on every summary sort (A69); this is a one-off result, so deriving beats store-and-verify. (D21)
A79. Known gap: a Score doesn't say which match it came from, so in a tournament-wide list two scores at 23:15 from different matches look alike. The proposed follow-up is to group per match (C6).
A80. Nothing matches → PlayerNotFoundException (a ScoreboardException). With no roster, this covers an unknown team, an unknown player and a real player who hasn't scored. Javadoc says so, and callers should read it as "no points recorded", not necessarily as an error. (D22)
A81. Arguments are validated before the lookup, with the same rules as Score (via PlayerKey): null → NPE, blank → IAE, shirt number outside 0–99 → IAE.
A82. The lookup scans the finished and live entries in start order and filters by PlayerKey. That's O(S) in tournament scores (World Cup: roughly 170 goals). An index would be a second structure to keep consistent, so it's a follow-up if profiling ever demands it. (D23)
A83. It runs under the same lock, for a consistent view across live and finished matches. It's a query: no state change, no event.
A84. Retained matches grow memory with the tournament. That's bounded by one tournament per instance (A4).
Plan-check decisions
A85. Base package is com.odds.scoreboard, groupId com.odds. This replaces the A5 placeholders.
A86. Kafka is out of scope: only the in-process ScoreboardListener is delivered (A40). kafka-clients leaves the pom (A2).
A87. Score gets a caller-supplied scoreId: non-blank, stripped, case-sensitive. It supersedes A16–A19 and A72 where they say updates aren't idempotent.
A88. A replayed Score (same match, same scoreId, same content) is a no-op: no state change, no event, returns the current Match.
A89. The same scoreId in the same match with different content throws IAE. Ids are unique per match, so another match may reuse one.
A90. PlayerScore.scores() is List<MatchScores(homeTeam, awayTeam, scores)> in match start order, scores by matchTime. Supersedes A78 and A79.
A91. No java.time.Clock is needed: ordering uses a sequence (A20) and matchTime is caller-supplied.
A92. Team names and surnames have no length cap; only strip() and non-blank apply, since the library is in-memory.
A93. scoreId is the hook for a future cancelScore(scoreId), which would lower a score (A17). It isn't built; the README lists it as a follow-up.
Remove last score (added after the first plan)
A94. removeLastScore(home, away) removes the last element of Match.scores(): latest matchTime, latest arrival among equal times. Not always the last received (A68).
A95. It returns the resulting Match. Unknown or finished match: MatchNotFoundException (A25). A live match with no scores: NoScoreToRemoveException (a ScoreboardException).
A96. A ScoreRemoved(match, score) event joins the sealed hierarchy: the resulting match plus the removed Score. One per success; rejected calls publish nothing (A42).
A97. A removed scoreId is free again in that match: posting it again records a new score, not a replay (A88).
A98. Only live matches are editable. Finished matches keep their scores. The player lookup reflects a removal at once, since it reads live matches (A83).
A99. Supersedes A17 and "scores can only go up"; this is a user decision. Removal by scoreId (A93) stays a follow-up. Start recency is unchanged (A22).

# Plan
- [x] Set package com.odds.scoreboard, drop App, trim pom to library setup — done when smoke: ./mvnw test green, -Xlint:all clean
- [x] Add Names, PlayerKey and Score record with scoreId and validation — done when unit tests: null→NPE, blank/out-of-range→IAE
- [x] Add Match snapshot record with invariants and withScore — done when unit tests: totals, chronological order, stable ties
- [x] Add Scoreboard, InMemoryScoreboard start/update/finish under one lock, with exceptions — done when unit tests: R3–R5, errors leave state unchanged
- [x] Make updateScore idempotent on scoreId: replay no-op, conflict throws — done when unit tests: replay unchanged, conflicting id IAE
- [x] Add getSummary ordered by total, then start sequence — done when unit test: equal totals list latest start first
- [x] Add concurrency tests for racing starts and updates — done when N-thread test: no lost points, one start wins
- [x] Add ScoreboardListener and sealed events, in own commit — done when unit tests: one event per success, replay and rejects silent
- [x] Retain finished matches, add getPlayerScore grouped per match, in own commit — done when unit tests: per-match groups, credited side, not-found
- [x] Write README.md and AI.md — done when manual review: assumptions, trade-offs, AI usage all covered
- [x] Add removeLastScore(home, away) with ScoreRemoved event and NoScoreToRemoveException — done when unit tests: totals drop, event published, empty match rejected
- [x] Update README, AI.md and notes for removeLastScore — done when manual review: no stale "scores only go up" text
