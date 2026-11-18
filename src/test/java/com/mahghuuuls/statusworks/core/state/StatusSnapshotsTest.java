package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusSnapshot;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusSnapshotsTest {

    private static final UUID A = new UUID(1L, 1L);
    private static final UUID B = new UUID(2L, 2L);

    private final Map<String, StatusDefinition> registry = new HashMap<>();
    private final List<EntityState.StatusSlot> slots = new ArrayList<>();

    private StatusDefinition define(StatusDefinition definition) {
        registry.put(definition.id(), definition);
        return definition;
    }

    /** Applies stacks as the status service does and puts the instance in the next slot. */
    private StatusState give(StatusDefinition def, UUID key, int stacks, int duration) {
        StatusState state = null;
        for (EntityState.StatusSlot s : slots) {
            if (s.id.equals(def.id())) {
                state = s.state;
            }
        }
        if (state == null) {
            state = new StatusState();
            slots.add(new EntityState.StatusSlot(def.id(), slots.size(), state));
        }
        StatusRules.apply(def, state, key, stacks, duration);
        return state;
    }

    private StatusSnapshot snapshot() {
        return StatusSnapshots.build(slots, registry::get);
    }

    private static StatusDefinition.Builder status(String id) {
        return StatusDefinition.builder(id).limit(10).defaultDuration(200);
    }

    @Test
    void anItemIconATextureIconAndNoIconAreGivenAsWritten() {
        StatusDefinition item = define(status("t:item").presentation(Presentation.builder().name("Rage")
                .iconItem("minecraft:blaze_powder", 2).build()).build());
        StatusDefinition texture = define(status("t:texture").presentation(Presentation.builder()
                .nameKey("t.texture.name").iconTexture("t:textures/gui/texture.png").build()).build());
        StatusDefinition plain = define(status("t:plain").build());
        give(item, StatusRules.NO_SOURCE, 1, 200);
        give(texture, StatusRules.NO_SOURCE, 1, 200);
        give(plain, StatusRules.NO_SOURCE, 1, 200);

        List<StatusSnapshot.Entry> rows = snapshot().entries();
        assertEquals(3, rows.size());
        assertEquals("t:item", rows.get(0).definitionId());
        assertEquals("Rage", rows.get(0).name());
        assertFalse(rows.get(0).nameIsKey());
        assertEquals("minecraft:blaze_powder", rows.get(0).iconItem());
        assertEquals(2, rows.get(0).iconMeta());
        assertNull(rows.get(0).iconTexture());
        assertEquals("t.texture.name", rows.get(1).name());
        assertTrue(rows.get(1).nameIsKey());
        assertNull(rows.get(1).iconItem());
        assertEquals("t:textures/gui/texture.png", rows.get(1).iconTexture());
        assertNull(rows.get(2).iconItem());
        assertNull(rows.get(2).iconTexture());
        assertEquals("plain", rows.get(2).name(), "without a name, the id's path, as on the HUD");
        assertFalse(rows.get(2).nameIsKey());
    }

    @Test
    void anAuthorHiddenStatusIsLeftOut() {
        StatusDefinition hidden = define(status("t:hidden").presentation(Presentation.builder().hidden().build())
                .build());
        StatusDefinition shown = define(status("t:shown").build());
        give(hidden, StatusRules.NO_SOURCE, 1, 200);
        give(shown, StatusRules.NO_SOURCE, 1, 200);

        List<StatusSnapshot.Entry> rows = snapshot().entries();
        assertEquals(1, rows.size());
        assertEquals("t:shown", rows.get(0).definitionId());
    }

    @Test
    void anUnregisteredDefinitionAndAnEmptyInstanceAreLeftOut() {
        StatusDefinition gone = status("t:gone").build();
        StatusDefinition kept = define(status("t:kept").build());
        give(gone, StatusRules.NO_SOURCE, 1, 200);
        give(kept, StatusRules.NO_SOURCE, 1, 200);
        slots.add(new EntityState.StatusSlot("t:empty", slots.size(), new StatusState()));
        define(status("t:empty").build());

        List<StatusSnapshot.Entry> rows = snapshot().entries();
        assertEquals(1, rows.size());
        assertEquals("t:kept", rows.get(0).definitionId());
    }

    @Test
    void noVisibleStatusGivesTheEmptySnapshot() {
        assertSame(StatusSnapshot.EMPTY, snapshot());
        assertTrue(snapshot().isEmpty());
    }

    @Test
    void rowsKeepRegistrationOrderAndCopyTheAuthorsPresentation() {
        StatusDefinition first = define(status("t:first").presentation(Presentation.builder().priority(7)
                .harmful().showCount(false).showTime(false).build()).build());
        StatusDefinition second = define(status("t:second").build());
        give(first, StatusRules.NO_SOURCE, 3, 200);
        give(second, StatusRules.NO_SOURCE, 1, 200);

        List<StatusSnapshot.Entry> rows = snapshot().entries();
        assertEquals("t:first", rows.get(0).definitionId());
        assertEquals("t:second", rows.get(1).definitionId());
        assertEquals(3, rows.get(0).count());
        assertTrue(rows.get(0).harmful());
        assertEquals(7, rows.get(0).priority());
        assertFalse(rows.get(0).showCount());
        assertFalse(rows.get(0).showTime());
        assertFalse(rows.get(1).harmful());
        assertEquals(Presentation.DEFAULT.priority(), rows.get(1).priority());
        assertEquals(Presentation.DEFAULT.showCount(), rows.get(1).showCount());
        assertEquals(Presentation.DEFAULT.showTime(), rows.get(1).showTime());
    }

    @Test
    void aTimedStatusGivesItsTimerAndAPermanentOneMinusOne() {
        StatusDefinition timed = define(status("t:timed").build());
        StatusDefinition permanent = define(status("t:permanent").expiration(ExpirationMode.NONE).build());
        StatusState timedState = give(timed, StatusRules.NO_SOURCE, 1, 200);
        give(permanent, StatusRules.NO_SOURCE, 1, 0);
        for (int i = 0; i < 50; i++) {
            StatusRules.tick(timed, timedState);
        }

        List<StatusSnapshot.Entry> rows = snapshot().entries();
        assertEquals(StatusRules.remaining(timed, timedState, null), rows.get(0).remainingTicks());
        assertEquals(150, rows.get(0).remainingTicks());
        assertEquals(-1, rows.get(1).remainingTicks());
    }

    @Test
    void aSharedStatusHasNoGroups() {
        StatusDefinition shared = define(status("t:shared").ownership(Ownership.SHARED).build());
        give(shared, StatusRules.NO_SOURCE, 4, 200);

        StatusSnapshot.Entry row = snapshot().entries().get(0);
        assertEquals(4, row.count());
        assertTrue(row.groups().isEmpty());
    }

    @Test
    void aPerSourceStatusGivesTheTotalTheSoonestTimeAndEachGroup() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        StatusState state = give(bleed, A, 2, 100);
        give(bleed, B, 3, 300);

        StatusSnapshot.Entry row = snapshot().entries().get(0);
        assertEquals(5, row.count());
        assertEquals(StatusRules.remaining(bleed, state, null), row.remainingTicks());
        assertEquals(100, row.remainingTicks());
        assertEquals(2, row.groups().size());
        Map<UUID, StatusSnapshot.Group> bySource = new HashMap<>();
        for (StatusSnapshot.Group g : row.groups()) {
            bySource.put(g.source(), g);
        }
        assertEquals(2, bySource.get(A).count());
        assertEquals(100, bySource.get(A).remainingTicks());
        assertEquals(3, bySource.get(B).count());
        assertEquals(300, bySource.get(B).remainingTicks());
    }

    @Test
    void stacksWithoutASourceFormTheNoSourceGroup() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        give(bleed, StatusRules.NO_SOURCE, 1, 100);

        StatusSnapshot.Group group = snapshot().entries().get(0).groups().get(0);
        assertEquals(StatusService.NO_SOURCE, group.source());
        assertEquals(1, group.count());
    }

    @Test
    void aSnapshotDoesNotChangeWhenTheStateChangesAfterIt() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        StatusState state = give(bleed, A, 2, 100);
        StatusSnapshot before = snapshot();

        give(bleed, B, 3, 300);
        for (int i = 0; i < 10; i++) {
            StatusRules.tick(bleed, state);
        }

        StatusSnapshot.Entry row = before.entries().get(0);
        assertEquals(2, row.count());
        assertEquals(100, row.remainingTicks());
        assertEquals(1, row.groups().size());
        assertEquals(5, snapshot().entries().get(0).count());
    }

    @Test
    void takingASnapshotChangesNothing() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        StatusState state = give(bleed, A, 2, 100);
        give(bleed, B, 3, 300);
        StatusSnapshot before = snapshot();

        snapshot();
        snapshot();

        assertEquals(before, snapshot(), "every row and every group, the non-soonest one included, is unchanged");
        assertEquals(5, state.total());
        assertEquals(2, StatusRules.count(state, A));
        assertEquals(3, StatusRules.count(state, B));
        assertEquals(300, StatusRules.remaining(bleed, state, B));
    }

    @Test
    void theConstructorsCopyTheListsTheyAreGiven() {
        List<StatusSnapshot.Group> groups = new ArrayList<>();
        groups.add(new StatusSnapshot.Group(A, 1, 10));
        StatusSnapshot.Entry entry = new StatusSnapshot.Entry("t:x", "X", false, null, 0, null, 1, 10, false, 0,
                true, true, groups);
        List<StatusSnapshot.Entry> entries = new ArrayList<>();
        entries.add(entry);
        StatusSnapshot s = new StatusSnapshot(entries);

        groups.clear();
        entries.clear();

        assertEquals(1, entry.groups().size());
        assertEquals(1, s.entries().size());
    }

    @Test
    void aNameKeyWithoutANameGivesTheIdsPathAsText() {
        StatusDefinition keyless = define(status("t:keyless").presentation(Presentation.builder().nameKey(null)
                .build()).build());
        give(keyless, StatusRules.NO_SOURCE, 1, 200);

        StatusSnapshot.Entry row = snapshot().entries().get(0);
        assertEquals("keyless", row.name());
        assertFalse(row.nameIsKey(), "the id's path is text, not a key");
    }

    @Test
    void equalityComparesEveryField() {
        StatusSnapshot.Entry base = entry("t:x", "X", 3, 100, Collections.singletonList(
                new StatusSnapshot.Group(A, 3, 100)));
        assertEquals(base, entry("t:x", "X", 3, 100, Collections.singletonList(new StatusSnapshot.Group(A, 3, 100))));
        assertFalse(base.equals(entry("t:y", "X", 3, 100, base.groups())), "id");
        assertFalse(base.equals(entry("t:x", "Y", 3, 100, base.groups())), "name");
        assertFalse(base.equals(entry("t:x", "X", 3, 99, base.groups())), "remaining time");
        assertFalse(base.equals(entry("t:x", "X", 3, 100, Collections.singletonList(
                new StatusSnapshot.Group(B, 3, 100)))), "group source");
        assertFalse(base.equals(entry("t:x", "X", 3, 100, Collections.singletonList(
                new StatusSnapshot.Group(A, 3, 99)))), "group time");
        assertFalse(base.equals(entry("t:x", "X", 3, 100, Collections.singletonList(
                new StatusSnapshot.Group(A, 2, 100)))), "group count");
        assertFalse(base.equals(entry("t:x", "X", 2, 100, base.groups())), "count");
        List<StatusSnapshot.Group> g = base.groups();
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", true, null, 0, null, 3, 100, false, 0, true,
                true, g)), "name is key");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, "minecraft:stone", 0, null, 3, 100,
                false, 0, true, true, g)), "icon item");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 1, null, 3, 100, false, 0, true,
                true, g)), "icon meta");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 0, "t:icon.png", 3, 100, false,
                0, true, true, g)), "icon texture");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 0, null, 3, 100, true, 0, true,
                true, g)), "harmful");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 0, null, 3, 100, false, 5, true,
                true, g)), "priority");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 0, null, 3, 100, false, 0, false,
                true, g)), "show count");
        assertFalse(base.equals(new StatusSnapshot.Entry("t:x", "X", false, null, 0, null, 3, 100, false, 0, true,
                false, g)), "show time");
    }

    @Test
    void anEntryRefusesANullIdOrName() {
        assertThrows(NullPointerException.class, () -> entry(null, "X", 1, 1, Collections.emptyList()));
        assertThrows(NullPointerException.class, () -> entry("t:x", null, 1, 1, Collections.emptyList()));
    }

    private static StatusSnapshot.Entry entry(String id, String name, int count, int remaining,
                                              List<StatusSnapshot.Group> groups) {
        return new StatusSnapshot.Entry(id, name, false, null, 0, null, count, remaining, false, 0, true, true,
                groups);
    }

    @Test
    void snapshotsWithTheSameRowsAreEqual() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        give(bleed, A, 2, 100);
        StatusSnapshot first = snapshot();
        StatusSnapshot second = snapshot();

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        give(bleed, B, 1, 100);
        assertFalse(first.equals(snapshot()));
    }

    @Test
    void theListsAreReadOnly() {
        StatusDefinition bleed = define(status("t:bleed").ownership(Ownership.PER_SOURCE).build());
        give(bleed, A, 2, 100);
        StatusSnapshot s = snapshot();

        assertThrows(UnsupportedOperationException.class, () -> s.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.entries().get(0).groups().clear());
    }
}
