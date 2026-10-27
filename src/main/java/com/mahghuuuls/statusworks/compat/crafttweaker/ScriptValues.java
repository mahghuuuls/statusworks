package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.StatusService;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Plain conversions between script values and API values: a source is a UUID in text, and no source is the empty
 * text. No CraftTweaker types, so it is unit-tested without the game.
 */
final class ScriptValues {

    private ScriptValues() {
    }

    /** A UUID from script text in its full 36-character form: the empty text is no source; other text gives null. */
    @Nullable
    static UUID uuid(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return StatusService.NO_SOURCE;
        }
        if (text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A UUID as script text: no source is the empty text. */
    static String text(@Nullable UUID id) {
        return id == null || StatusService.NO_SOURCE.equals(id) ? "" : id.toString();
    }

    static String[] texts(List<UUID> ids) {
        String[] out = new String[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = text(ids.get(i));
        }
        return out;
    }

    /** The failure text for a source that is not a UUID. */
    static String notAUuid(String text) {
        return "\"" + text + "\" is not a UUID";
    }
}
