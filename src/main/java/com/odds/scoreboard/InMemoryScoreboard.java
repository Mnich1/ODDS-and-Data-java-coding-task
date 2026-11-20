package com.odds.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * In-memory {@link Scoreboard}. One lock guards all state, so check-then-act sequences such as
 * "neither team is playing, then insert" are atomic. A ReentrantLock is used instead of
 * synchronized because synchronized pins virtual threads on JDK 21.
 */
public final class InMemoryScoreboard implements Scoreboard {

    private record MatchKey(String homeTeam, String awayTeam) {

        MatchKey {
            homeTeam = Names.normalize(homeTeam, "homeTeam");
            awayTeam = Names.normalize(awayTeam, "awayTeam");
        }
    }

    private static final System.Logger LOG = System.getLogger(InMemoryScoreboard.class.getName());

    private final ReentrantLock lock = new ReentrantLock();
    /** Rarely registered, often iterated. */
    private final List<ScoreboardListener> listeners = new CopyOnWriteArrayList<>();
    /** Live matches in start order. */
    private final Map<MatchKey, Match> liveMatches = new LinkedHashMap<>();
    /** Finished matches in finish order. Kept only for the player lookup, never exposed directly. */
    private final List<Match> finishedMatches = new ArrayList<>();

    @Override
    public Match startMatch(String homeTeam, String awayTeam) {
        Match match = Match.start(homeTeam, awayTeam);
        return locked(() -> {
            requireAvailable(match.homeTeam());
            requireAvailable(match.awayTeam());
            liveMatches.put(new MatchKey(match.homeTeam(), match.awayTeam()), match);
            publish(new ScoreboardEvent.MatchStarted(match));
            return match;
        });
    }

    @Override
    public Match updateScore(String homeTeam, String awayTeam, Score score) {
        MatchKey key = new MatchKey(homeTeam, awayTeam);
        Objects.requireNonNull(score, "score must not be null");
        return locked(() -> {
            Match current = liveMatch(key);
            Match updated = current.withScore(score);
            liveMatches.put(key, updated);
            // withScore returns the same snapshot for a replay, which publishes nothing
            if (updated != current) {
                publish(new ScoreboardEvent.ScoreRecorded(updated, score));
            }
            return updated;
        });
    }

    @Override
    public Match removeLastScore(String homeTeam, String awayTeam) {
        MatchKey key = new MatchKey(homeTeam, awayTeam);
        return locked(() -> {
            Match current = liveMatch(key);
            Match updated = current.withoutLastScore();
            liveMatches.put(key, updated);
            publish(new ScoreboardEvent.ScoreRemoved(updated, current.scores().getLast()));
            return updated;
        });
    }

    @Override
    public Match finishMatch(String homeTeam, String awayTeam) {
        MatchKey key = new MatchKey(homeTeam, awayTeam);
        return locked(() -> {
            Match finished = liveMatch(key);
            liveMatches.remove(key);
            finishedMatches.add(finished);
            publish(new ScoreboardEvent.MatchFinished(finished));
            return finished;
        });
    }

    @Override
    public void addListener(ScoreboardListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /** Called with the lock held, after the state changed. A failing listener must not affect the others. */
    private void publish(ScoreboardEvent event) {
        for (ScoreboardListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Listener failed while handling " + event.getClass().getSimpleName(), e);
            }
        }
    }

    @Override
    public List<Match> getSummary() {
        return locked(() -> {
            List<Match> mostRecentFirst = new ArrayList<>(liveMatches.values());
            Collections.reverse(mostRecentFirst);
            // sorted() is stable, so equal totals keep the most-recently-started-first order
            return mostRecentFirst.stream()
                    .sorted(Comparator.comparingInt(InMemoryScoreboard::totalScore).reversed())
                    .toList();
        });
    }

    @Override
    public PlayerScore getPlayerScore(String team, int shirtNumber, String playerSurname) {
        PlayerKey player = new PlayerKey(team, shirtNumber, playerSurname);
        return locked(() -> {
            // A team plays one live match at a time, so a player's finished matches are already in
            // start order, followed by at most one live match.
            List<PlayerScore.MatchScores> matches =
                    Stream.concat(finishedMatches.stream(), liveMatches.values().stream())
                            .map(match -> new PlayerScore.MatchScores(match.homeTeam(), match.awayTeam(),
                                    match.scores().stream()
                                            .filter(score -> score.playerKey().equals(player))
                                            .toList()))
                            .filter(matchScores -> !matchScores.scores().isEmpty())
                            .toList();
            return Optional.of(matches)
                    .filter(found -> !found.isEmpty())
                    .map(found -> new PlayerScore(player.team(), player.shirtNumber(),
                            player.playerSurname(), found))
                    .orElseThrow(() -> new PlayerNotFoundException(player));
        });
    }

    private static int totalScore(Match match) {
        return match.homeScore() + match.awayScore();
    }

    private Match liveMatch(MatchKey key) {
        return Optional.ofNullable(liveMatches.get(key))
                .orElseThrow(() -> new MatchNotFoundException(key.homeTeam(), key.awayTeam()));
    }

    private void requireAvailable(String team) {
        liveMatches.values().stream()
                .filter(live -> live.homeTeam().equals(team) || live.awayTeam().equals(team))
                .findFirst()
                .ifPresent(live -> {
                    throw new TeamAlreadyPlayingException(team, live);
                });
    }

    private <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
