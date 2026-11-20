package com.odds.scoreboard;

/**
 * Thrown when a match is started with a team that is already playing a live match.
 */
public class TeamAlreadyPlayingException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    TeamAlreadyPlayingException(String team, Match liveMatch) {
        super("Team '" + team + "' is already playing in " + liveMatch.homeTeam() + " " + liveMatch.homeScore()
                + " - " + liveMatch.awayTeam() + " " + liveMatch.awayScore());
    }
}
