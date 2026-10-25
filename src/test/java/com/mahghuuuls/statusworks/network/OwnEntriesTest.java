package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.mark.MarkView;
import com.mahghuuuls.statusworks.core.rule.CooldownRules;
import com.mahghuuuls.statusworks.core.rule.PoolMath;
import com.mahghuuuls.statusworks.core.rule.PoolState;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import com.mahghuuuls.statusworks.core.state.EntityState;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnEntriesTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID ZOMBIE = new UUID(3, 3);

    private final DefinitionRegistry registry = new DefinitionRegistry();
    private final StatusDefinition shown = StatusDefinition.builder("t:shown").limit(5).defaultDuration(200)
            .presentation(Presentation.builder().name("Shown").build()).build();
    private final StatusDefinition hidden = StatusDefinition.builder("t:hidden").defaultDuration(200)
            .presentation(Presentation.builder().hidden().build()).build();
    private final StatusDefinition perSource = StatusDefinition.builder("t:per").limit(10).defaultDuration(400)
            .ownership(Ownership.PER_SOURCE).build();
    private final StatusDefinition none = StatusDefinition.builder("t:none").expiration(ExpirationMode.NONE).build();
    private final PoolDefinition pool = PoolDefinition.builder("t:pool").capacity(50)
            .presentation(Presentation.builder().decimals(1).build()).build();
    private final MarkDefinition hunt = MarkDefinition.builder("t:hunt").build();
    private final MarkDefinition both = MarkDefinition.builder("t:both")
            .presentation(Presentation.builder().markHud(true, true).build()).build();
    private final MarkDefinition neither = MarkDefinition.builder("t:neither")
            .presentation(Presentation.builder().markHud(false, false).build()).build();
    private final CooldownDefinition dash = CooldownDefinition.builder("t:dash").build();

    OwnEntriesTest() {
        for (StatusDefinition d : Arrays.asList(shown, hidden, perSource, none)) {
            registry.registerStatus(d, "test");
        }
        registry.registerPool(pool, "test");
        registry.registerMark(hunt, "test");
        registry.registerMark(both, "test");
        registry.registerMark(neither, "test");
        registry.registerCooldown(dash, "test");
        assertTrue(registry.freeze().isEmpty());
    }

    private StatusState status(EntityState state, StatusDefinition d) {
        return state.statusOrCreate(d.id(), registry.status(d.id()).order());
    }

    @Test
    void anAuthorHiddenDefinitionHasNoRowAndNoEntry() {
        PresentationTable table = PresentationTable.build(registry);
        for (HudDefinition row : table.rows()) {
            assertFalse(row.id.equals("t:hidden") || row.id.equals("t:neither"), "hidden rows never exist: " + row.id);
        }
        EntityState state = new EntityState();
        StatusRules.apply(hidden, status(state, hidden), StatusRules.NO_SOURCE, 1, 200);
        Map<String, HudEntry> entries = OwnEntries.build(table, registry, state,
                Collections.singletonList(new MarkView("t:neither", true, ZOMBIE, "Zombie", -1, true)), false, id -> "x");
        assertTrue(entries.isEmpty(), "nothing of a hidden definition is built, so nothing can be sent");
    }

    @Test
    void tableRowsCarryNameIconFieldsAndRegistrationOrder() {
        PresentationTable table = PresentationTable.build(registry);
        HudDefinition first = table.rows().get(0);
        assertEquals("t:shown", first.id);
        assertEquals("Shown", first.name);
        assertEquals(5, first.limit);
        assertEquals("shared", first.timerMode);
        assertEquals("per", table.status("t:per").name, "no name: the id's path");
        assertEquals(1, table.pool("t:pool").decimals);
        assertEquals("lifetime", table.pool("t:pool").timerMode);
        assertTrue(table.rows().indexOf(table.pool("t:pool")) > table.rows().indexOf(table.status("t:none")));
        assertNull(table.mark("t:hunt", false), "a mark shows on the source's HUD only by default");
        assertEquals("t:both", table.mark("t:both", false).id);
    }

    @Test
    void aPerSourceStatusIsOneEntryWithTheTotalAndTheSoonestTimer() {
        EntityState state = new EntityState();
        StatusState s = status(state, perSource);
        StatusRules.apply(perSource, s, A, 2, 200);
        StatusRules.apply(perSource, s, B, 3, 80);
        Map<String, HudEntry> entries = OwnEntries.build(PresentationTable.build(registry), registry, state,
                Collections.<MarkView>emptyList(), false, id -> id.equals(A) ? "Alex" : "Bo");
        assertEquals(1, entries.size());
        HudEntry e = entries.values().iterator().next();
        assertEquals(5, e.count);
        assertEquals(80, e.remaining, "4 seconds, the soonest");
        assertEquals(2, e.groups.size());
        assertEquals("Alex", e.groups.get(0).source);
        assertEquals(200, e.groups.get(0).remaining);
        assertEquals(3, e.groups.get(1).count);
    }

    @Test
    void poolsRoundToTheirDecimalsMarksAndCooldownsBuildTheirEntries() {
        EntityState state = new EntityState();
        PoolState p = PoolMath.create(pool);
        PoolMath.set(p, 12.34);
        state.putPool("t:pool", registry.pool("t:pool").order(), p);
        state.putCooldown("t:dash", registry.cooldown("t:dash").order(), CooldownRules.start(60));
        StatusRules.apply(none, status(state, none), StatusRules.NO_SOURCE, 1, 0);
        List<MarkView> marks = new ArrayList<>();
        marks.add(new MarkView("t:hunt", true, ZOMBIE, "Zombie", 300, true));
        marks.add(new MarkView("t:hunt", false, A, "Alex", 300, true));
        marks.add(new MarkView("t:both", false, B, "Bo", -1, false));
        Map<String, HudEntry> entries = OwnEntries.build(PresentationTable.build(registry), registry, state, marks,
                false, id -> "x");
        HudEntry poolEntry = find(entries, "t:pool");
        assertEquals(12.3, poolEntry.amount, 1e-9);
        assertEquals(50, poolEntry.capacity, 1e-9);
        assertEquals(-1, poolEntry.remaining, "no lifetime");
        assertEquals(60, find(entries, "t:dash").remaining);
        assertEquals(-1, find(entries, "t:none").remaining, "a status without a timer shows none");
        int hunts = 0;
        for (HudEntry e : entries.values()) {
            if (e.definition == PresentationTable.build(registry).mark("t:hunt", true).index) {
                hunts++;
                assertEquals(HudEntry.MARK_SOURCE, e.side, "the target side of hunt is not shown");
                assertEquals("Zombie", e.other);
            }
        }
        assertEquals(1, hunts);
        HudEntry linked = find(entries, "t:both");
        assertEquals(HudEntry.MARK_TARGET, linked.side);
        assertFalse(linked.active);
    }

    @Test
    void aPausedPlayersEntriesAreInactiveSoTheClientDoesNotCountThemDown() {
        EntityState state = new EntityState();
        state.putCooldown("t:dash", registry.cooldown("t:dash").order(), CooldownRules.start(60));
        Map<String, HudEntry> dead = OwnEntries.build(PresentationTable.build(registry), registry, state,
                Collections.<MarkView>emptyList(), true, id -> "x");
        HudEntry e = dead.values().iterator().next();
        assertFalse(e.active);
        assertFalse(e.countsDown());
        EntryDiff diff = new EntryDiff();
        diff.update(dead, 0);
        assertTrue(diff.update(dead, 100).isEmpty(), "a frozen timer is not resent while the player stays dead");
    }

    @Test
    void messagesRoundTripThroughBytes() {
        PresentationTable table = PresentationTable.build(registry);
        ByteBuf buf = Unpooled.buffer();
        new TableMessage(table.rows()).toBytes(buf);
        TableMessage back = new TableMessage();
        back.fromBytes(buf);
        assertEquals(table.rows().size(), back.rows.size());
        assertEquals("Shown", back.rows.get(0).name);
        assertEquals(1, back.rows.get(table.rows().indexOf(table.pool("t:pool"))).decimals);

        List<HudEntry> changed = new ArrayList<>();
        changed.add(new HudEntry("3|s:x", 3, HudEntry.MARK_SOURCE, 0, 0, 0, 120, false, "Zombie",
                Collections.<HudEntry.Group>emptyList()));
        changed.add(new HudEntry("2|", 2, HudEntry.OWN, 5, 0, 0, 80, true, null,
                Collections.singletonList(new HudEntry.Group("Alex", 2, 200))));
        ByteBuf buf2 = Unpooled.buffer();
        new EntriesMessage(true, changed, Collections.singletonList("9|")).toBytes(buf2);
        EntriesMessage e = new EntriesMessage();
        e.fromBytes(buf2);
        assertTrue(e.snapshot);
        assertEquals("Zombie", e.changed.get(0).other);
        assertFalse(e.changed.get(0).active);
        assertEquals("Alex", e.changed.get(1).groups.get(0).source);
        assertEquals("9|", e.removed.get(0));
    }

    private static HudEntry find(Map<String, HudEntry> entries, String id) {
        for (HudEntry e : entries.values()) {
            if (e.key.equals(keyOf(id))) {
                return e;
            }
        }
        throw new AssertionError("no entry for " + id);
    }

    private static String keyOf(String id) {
        return KEYS.get(id);
    }

    private static final Map<String, String> KEYS = new java.util.HashMap<>();

    static {
        // Row indexes of the fixed registration above: statuses shown, per, none; pool; marks hunt, both; cooldown.
        KEYS.put("t:pool", "3|");
        KEYS.put("t:none", "2|");
        KEYS.put("t:dash", "6|");
        KEYS.put("t:both", "5|t:" + B);
    }
}
