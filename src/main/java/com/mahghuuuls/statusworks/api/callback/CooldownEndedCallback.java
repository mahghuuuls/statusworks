package com.mahghuuuls.statusworks.api.callback;

/**
 * Runs when a cooldown ends, with the reason: {@code expired} when its time ran out, {@code removed} when an
 * operation ended it (clear, or reduce to 0), {@code death} when its entity died and its death rule does not keep it.
 */
@FunctionalInterface
public interface CooldownEndedCallback {
    void onEnded(CooldownContext context);
}
