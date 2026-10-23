package com.mahghuuuls.statusworks.core.state;

import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.rule.StatusState;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityStateCodecTest {

    private static final UUID A = new UUID(5, 7);

    @Test
    void roundTripKeepsCountsTimersGroupsAndOrder() {
        DefinitionRegistry registry = new DefinitionRegistry();
        StatusDefinition shared = StatusDefinition.builder("t:shared").limit(5).defaultDuration(100).build();
        StatusDefinition indep = StatusDefinition.builder("t:indep").limit(10).defaultDuration(100)
                .expiration(ExpirationMode.INDEPENDENT).ownership(Ownership.PER_SOURCE).build();
        registry.registerStatus(shared, "test");
        registry.registerStatus(indep, "test");
        registry.freeze();

        EntityState state = new EntityState();
        StatusState i = state.statusOrCreate("t:indep", registry.status("t:indep").order());
        StatusRules.apply(indep, i, A, 2, 50);
        StatusRules.apply(indep, i, StatusRules.NO_SOURCE, 1, 80);
        StatusState s = state.statusOrCreate("t:shared", registry.status("t:shared").order());
        StatusRules.apply(shared, s, StatusRules.NO_SOURCE, 3, 100);
        for (int t = 0; t < 30; t++) {
            StatusRules.tick(shared, s);
            StatusRules.tick(indep, i);
        }

        List<String> dropped = new ArrayList<>();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> dropped.add(id));
        NBTTagCompound nbt = codec.write(state);
        EntityState restored = new EntityState();
        codec.read(restored, nbt);

        assertEquals(1, nbt.getInteger("v"));
        assertEquals("t:shared", restored.statuses().get(0).id, "registration order is kept");
        StatusState rs = restored.status("t:shared");
        StatusState ri = restored.status("t:indep");
        assertEquals(3, rs.total());
        assertEquals(70, StatusRules.remaining(shared, rs, null));
        assertEquals(2, StatusRules.count(ri, A));
        assertEquals(1, StatusRules.count(ri, StatusRules.NO_SOURCE));
        assertEquals(20, StatusRules.remaining(indep, ri, A));
        assertEquals(50, StatusRules.remaining(indep, ri, StatusRules.NO_SOURCE));
        assertEquals(30, ri.age());
        assertEquals(0, dropped.size());
    }

    @Test
    void emptyStateWritesNothingAndANewerFormatIsNotRead() {
        DefinitionRegistry registry = new DefinitionRegistry();
        StatusDefinition d = StatusDefinition.builder("t:s").build();
        registry.registerStatus(d, "test");
        registry.freeze();
        List<String> dropped = new ArrayList<>();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> dropped.add(why));
        assertEquals(0, codec.write(new EntityState()).getSize());

        EntityState state = new EntityState();
        StatusRules.apply(d, state.statusOrCreate("t:s", 0), StatusRules.NO_SOURCE, 1, 0);
        NBTTagCompound nbt = codec.write(state);
        nbt.setInteger("v", EntityStateCodec.VERSION + 1);
        EntityState restored = new EntityState();
        codec.read(restored, nbt);
        assertTrue(restored.isEmpty());
        assertEquals(1, dropped.size());
        assertEquals(nbt, codec.write(restored), "a newer format is saved back unchanged");
    }

    @Test
    void savedStateOfAnUnregisteredDefinitionIsDropped() {
        DefinitionRegistry before = new DefinitionRegistry();
        StatusDefinition gone = StatusDefinition.builder("t:gone").build();
        before.registerStatus(gone, "test");
        before.freeze();
        EntityState state = new EntityState();
        StatusRules.apply(gone, state.statusOrCreate("t:gone", 0), StatusRules.NO_SOURCE, 1, 0);
        NBTTagCompound nbt = new EntityStateCodec(before, (id, why) -> { }).write(state);

        DefinitionRegistry after = new DefinitionRegistry();
        after.freeze();
        List<String> dropped = new ArrayList<>();
        EntityState restored = new EntityState();
        new EntityStateCodec(after, (id, why) -> dropped.add(id)).read(restored, nbt);
        assertNull(restored.status("t:gone"));
        assertEquals(1, dropped.size());
        List<EntityState.LoadNote> notes = restored.takeLoadNotes();
        assertEquals(1, notes.size());
        assertEquals("t:gone", notes.get(0).id);
        assertEquals(1, notes.get(0).countBefore);
        assertEquals(0, notes.get(0).countAfter);
        assertEquals("invalid", notes.get(0).reason);
        assertTrue(restored.takeLoadNotes().isEmpty(), "notes are reported once");
    }

    @Test
    void aSavedCountAboveALoweredLimitIsClampedOnLoad() {
        DefinitionRegistry before = new DefinitionRegistry();
        StatusDefinition wide = StatusDefinition.builder("t:s").limit(10).defaultDuration(100).build();
        before.registerStatus(wide, "test");
        before.freeze();
        EntityState state = new EntityState();
        StatusRules.apply(wide, state.statusOrCreate("t:s", 0), StatusRules.NO_SOURCE, 8, 0);
        NBTTagCompound nbt = new EntityStateCodec(before, (id, why) -> { }).write(state);

        DefinitionRegistry after = new DefinitionRegistry();
        after.registerStatus(StatusDefinition.builder("t:s").limit(5).defaultDuration(100).build(), "test");
        after.freeze();
        List<String> warned = new ArrayList<>();
        EntityState restored = new EntityState();
        new EntityStateCodec(after, (id, why) -> warned.add(id)).read(restored, nbt);
        assertEquals(5, restored.status("t:s").total());
        assertEquals(java.util.Collections.singletonList("fit:t:s"), warned);
        EntityState.LoadNote note = restored.takeLoadNotes().get(0);
        assertEquals(8, note.countBefore);
        assertEquals(5, note.countAfter);
        assertNull(note.reason);
    }

    @Test
    void anUnchangedDefinitionLeavesNoLoadNote() {
        DefinitionRegistry registry = new DefinitionRegistry();
        StatusDefinition d = StatusDefinition.builder("t:s").limit(5).defaultDuration(100)
                .expiration(ExpirationMode.GRADUAL).build();
        registry.registerStatus(d, "test");
        registry.freeze();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> { });
        EntityState state = new EntityState();
        StatusRules.apply(d, state.statusOrCreate("t:s", 0), StatusRules.NO_SOURCE, 3, 0);
        EntityState restored = new EntityState();
        codec.read(restored, codec.write(state));
        assertTrue(restored.takeLoadNotes().isEmpty());
        assertEquals(3, restored.status("t:s").total());
    }
}
