package com.lavochk.listTelegram;

public class PlayerNameUtil {

    /**
     * Normalizes a player name by removing a leading dot, if present.
     * This handles cases like ".Kyle" and "Kyle" being the same player.
     * @param playerName The player name to normalize.
     * @return The normalized player name.
     */
    public static String normalize(String playerName) {
        if (playerName != null && playerName.startsWith(".")) {
            return playerName.substring(1);
        }
        return playerName;
    }
}
