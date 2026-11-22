package com.minebridge.death;

import java.util.UUID;

public record DeathContext(
        String victim,
        String killer,
        String cause,
        DeathCategory category,
        UUID victimUuid) {

    public DeathContext(
            String victim,
            String killer,
            String cause,
            DeathCategory category) {
        this(victim, killer, cause, category, null);
    }

    public DeathContext {
        victim = victim == null ? "" : victim;
        killer = killer == null ? "" : killer;
        cause = cause == null ? "an unknown cause" : cause;
    }
}
