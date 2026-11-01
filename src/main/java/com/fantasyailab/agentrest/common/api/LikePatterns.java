package com.fantasyailab.agentrest.common.api;

import java.util.Locale;

/** Builds safe, case-insensitive "contains" LIKE patterns. Escape character is {@code '!'}. */
public final class LikePatterns {

    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    public static String contains(String raw) {
        String escaped = raw.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
