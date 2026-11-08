package com.mahghuuuls.statusworks.core.lifecycle;

/**
 * Whether the entity of a join event is in the world. {@code World.spawnEntity} adds a player or a force-spawned
 * entity (a mob arriving through a portal) even when another mod canceled its join; any other canceled join, and
 * every canceled join from loaded chunks, means the entity was not added.
 */
final class JoinRule {

    private JoinRule() {
    }

    static boolean added(boolean canceled, boolean player, boolean forceSpawn) {
        return !canceled || player || forceSpawn;
    }
}
