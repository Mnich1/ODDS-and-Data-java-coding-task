package com.odds.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Releases N threads at the same moment against the real {@link InMemoryScoreboard}. What decides
 * each race is the single lock, so a lost point or a second winner means the lock is broken.
 */
class ConcurrentScoreboardTest {

    private static final int THREADS = 32;

    private InMemoryScoreboard board;

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
    }

    private record Result<T>(T value, RuntimeException error) {

        boolean succeeded() {
            return error == null;
        }
    }

    /** Runs one task per thread, all released together by a latch, and collects value or exception. */
    private static <T> List<Result<T>> race(int threads, IntFunction<Callable<T>> taskFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Result<T>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskFor.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return new Result<>(task.call(), null);
                    } catch (RuntimeException e) {
                        return new Result<>(null, e);
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Result<T>> results = new ArrayList<>();
            for (Future<Result<T>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Score goal(String team, int seconds, String id) {
        return Score.goal(team, 9, "Player", Duration.ofSeconds(seconds), id);
    }

    @Test
    void racingStartsOfTheSamePairHaveExactlyOneWinner() throws Exception {
        List<Result<Match>> results = race(THREADS, i -> () -> board.startMatch("Spain", "Brazil"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(TeamAlreadyPlayingException.class));
        assertThat(board.getSummary()).containsExactly(Match.start("Spain", "Brazil"));
    }

    @Test
    void racingStartsSharingOneTeamAsHomeOrAwayHaveExactlyOneWinner() throws Exception {
        List<Result<Match>> results = race(THREADS, i -> () -> i % 2 == 0
                ? board.startMatch("Spain", "Opponent" + i)
                : board.startMatch("Opponent" + i, "Spain"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(TeamAlreadyPlayingException.class));
        assertThat(board.getSummary()).hasSize(1);
    }

    @Test
    void concurrentUpdatesOfOneMatchLoseNoPoints() throws Exception {
        board.startMatch("Spain", "Brazil");
        int updatesPerThread = 50;

        List<Result<Match>> results = race(THREADS, thread -> () -> {
            Match last = null;
            for (int j = 0; j < updatesPerThread; j++) {
                String team = j % 2 == 0 ? "Spain" : "Brazil";
                last = board.updateScore("Spain", "Brazil", goal(team, j, "t" + thread + "-" + j));
            }
            return last;
        });

        assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
        Match match = board.getSummary().getFirst();
        int expectedPerSide = THREADS * updatesPerThread / 2;
        assertThat(match.homeScore()).isEqualTo(expectedPerSide);
        assertThat(match.awayScore()).isEqualTo(expectedPerSide);
        assertThat(match.scores()).hasSize(THREADS * updatesPerThread);
    }

    @Test
    void concurrentReplaysOfTheSameScoreAreCountedOnce() throws Exception {
        board.startMatch("Spain", "Brazil");
        Score score = goal("Spain", 10, "s1");

        List<Result<Match>> results = race(THREADS, i -> () -> board.updateScore("Spain", "Brazil", score));

        assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
        Match match = board.getSummary().getFirst();
        assertThat(match.homeScore()).isEqualTo(1);
        assertThat(match.scores()).containsExactly(score);
    }

    @Test
    void racingFinishesOfTheSameMatchHaveExactlyOneWinner() throws Exception {
        board.startMatch("Spain", "Brazil");

        List<Result<Match>> results = race(THREADS, i -> () -> board.finishMatch("Spain", "Brazil"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(MatchNotFoundException.class));
        assertThat(board.getSummary()).isEmpty();
    }

    // --- removeLastScore under contention ---

    @Test
    void racingRemovalsOfASingleScoreHaveExactlyOneWinner() throws Exception {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        List<Result<Match>> results = race(THREADS, i -> () -> board.removeLastScore("Spain", "Brazil"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(NoScoreToRemoveException.class));
        assertThat(board.getSummary()).containsExactly(Match.start("Spain", "Brazil"));
    }

    @Test
    void concurrentUpdatesAndRemovalsLoseNoPoints() throws Exception {
        int preloaded = 400;
        int updatesPerAdder = 20;
        int removalsPerRemover = 10;
        board.startMatch("Spain", "Brazil");
        for (int j = 0; j < preloaded; j++) {
            board.updateScore("Spain", "Brazil", goal("Spain", j, "p" + j));
        }

        List<Result<Match>> results = race(THREADS, thread -> () -> thread % 2 == 0
                ? addScores(thread, updatesPerAdder)
                : removeScores(removalsPerRemover));

        assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
        int adders = THREADS / 2;
        int removers = THREADS - adders;
        int expected = preloaded + adders * updatesPerAdder - removers * removalsPerRemover;
        Match match = board.getSummary().getFirst();
        assertThat(match.scores()).hasSize(expected);
        assertThat(match.homeScore()).isEqualTo(expected);
        assertThat(match.awayScore()).isZero();
    }

    @Test
    void updatesRacingAFinishAreEitherInTheFinalResultOrRejected() throws Exception {
        // The window between "read the live match" and "put it back" is tiny, so repeat the race
        // on fresh boards. Without the lock a finished match gets resurrected or loses a point.
        for (int round = 0; round < 100; round++) {
            board = new InMemoryScoreboard();
            board.startMatch("Spain", "Brazil");
            IntFunction<Callable<Object>> task = thread -> () -> {
                if (thread == 0) {
                    return board.finishMatch("Spain", "Brazil");
                }
                return recordUntilRejected(thread, 20);
            };

            List<Result<Object>> results = race(THREADS, task);

            assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
            Match finalResult = (Match) results.getFirst().value();
            int recorded = results.stream().skip(1).mapToInt(r -> (Integer) r.value()).sum();
            assertThat(finalResult.scores()).hasSize(recorded);
            assertThat(finalResult.homeScore()).isEqualTo(recorded);
            assertThat(board.getSummary()).isEmpty();
            assertThatThrownBy(() -> board.updateScore("Spain", "Brazil", goal("Spain", 1, "late")))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    // --- listener order under contention ---

    @Test
    void eventsReachEveryListenerInTheExactOrderTheStateChanged() throws Exception {
        board.startMatch("Spain", "Brazil");
        // plain lists on purpose: delivery happens inside the lock, one thread at a time
        List<Integer> first = new ArrayList<>();
        List<Integer> second = new ArrayList<>();
        board.addListener(event -> first.add(homeScoreOf(event)));
        board.addListener(event -> second.add(homeScoreOf(event)));
        int updatesPerThread = 25;

        race(THREADS, thread -> () -> addScores(thread, updatesPerThread));

        List<Integer> everyTotalInOrder = IntStream.rangeClosed(1, THREADS * updatesPerThread).boxed().toList();
        assertThat(first).isEqualTo(everyTotalInOrder);
        assertThat(second).isEqualTo(everyTotalInOrder);
    }

    @Test
    void eventsStayInStateOrderWhenUpdatesAndRemovalsInterleave() throws Exception {
        int preloaded = 100;
        board.startMatch("Spain", "Brazil");
        for (int j = 0; j < preloaded; j++) {
            board.updateScore("Spain", "Brazil", goal("Spain", j, "p" + j));
        }
        List<ScoreboardEvent> events = new ArrayList<>();
        board.addListener(events::add);

        race(THREADS, thread -> () -> thread % 2 == 0 ? addScores(thread, 20) : removeScores(10));

        int previous = preloaded;
        for (ScoreboardEvent event : events) {
            int expected = event instanceof ScoreboardEvent.ScoreRecorded ? previous + 1 : previous - 1;
            assertThat(homeScoreOf(event)).isEqualTo(expected);
            previous = expected;
        }
        assertThat(events).hasSize(THREADS / 2 * 20 + THREADS / 2 * 10);
    }

    private Match addScores(int thread, int count) {
        Match last = null;
        for (int j = 0; j < count; j++) {
            last = board.updateScore("Spain", "Brazil", goal("Spain", 1_000 + j, "a" + thread + "-" + j));
        }
        return last;
    }

    private Match removeScores(int count) {
        Match last = null;
        for (int j = 0; j < count; j++) {
            last = board.removeLastScore("Spain", "Brazil");
        }
        return last;
    }

    /** Records until the match is finished by someone else, and returns how many scores it recorded. */
    private int recordUntilRejected(int thread, int count) {
        int recorded = 0;
        try {
            for (int j = 0; j < count; j++) {
                board.updateScore("Spain", "Brazil", goal("Spain", j, "t" + thread + "-" + j));
                recorded++;
            }
        } catch (MatchNotFoundException finishedFirst) {
            return recorded;
        }
        return recorded;
    }

    private static int homeScoreOf(ScoreboardEvent event) {
        return switch (event) {
            case ScoreboardEvent.ScoreRecorded recorded -> recorded.match().homeScore();
            case ScoreboardEvent.ScoreRemoved removed -> removed.match().homeScore();
            case ScoreboardEvent.MatchStarted started -> started.match().homeScore();
            case ScoreboardEvent.MatchFinished finished -> finished.finalResult().homeScore();
        };
    }
}
