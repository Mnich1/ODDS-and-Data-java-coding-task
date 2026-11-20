package com.odds.scoreboard;

/**
 * Thrown when no points are recorded for a player. The board has no roster, so this covers an
 * unknown team, an unknown player and a real player who hasn't scored. Callers should read it as
 * "no points recorded", not necessarily as an error.
 */
public class PlayerNotFoundException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    PlayerNotFoundException(PlayerKey player) {
        super("No points recorded for '" + player.playerSurname() + "' (#" + player.shirtNumber()
                + ") of '" + player.team() + "'");
    }
}
