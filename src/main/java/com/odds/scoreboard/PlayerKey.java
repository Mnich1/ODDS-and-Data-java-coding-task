package com.odds.scoreboard;

/**
 * Identity of a player: team, shirt number and surname, all matched exactly after stripping.
 */
record PlayerKey(String team, int shirtNumber, String playerSurname) {

    static final int MIN_SHIRT_NUMBER = 0;
    static final int MAX_SHIRT_NUMBER = 99;

    PlayerKey {
        team = Names.normalize(team, "team");
        playerSurname = Names.normalize(playerSurname, "playerSurname");
        if (shirtNumber < MIN_SHIRT_NUMBER || shirtNumber > MAX_SHIRT_NUMBER) {
            throw new IllegalArgumentException("shirtNumber must be between " + MIN_SHIRT_NUMBER
                    + " and " + MAX_SHIRT_NUMBER + " but was " + shirtNumber);
        }
    }
}
