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

        assertEquals(EntityStateCodec.VERSION, nbt.getInteger("v"));
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
    void poolsRoundTripAndAVersionOneSaveStillLoads() {
        DefinitionRegistry registry = new DefinitionRegistry();
        com.mahghuuuls.statusworks.api.definition.PoolDefinition p = com.mahghuuuls.statusworks.api.definition
                .PoolDefinition.builder("t:pool").capacity(20).startAmount(10).toTarget(0, 100).lifetime(400)
                .build();
        registry.registerPool(p, "test");
        registry.freeze();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> { });
        EntityState state = new EntityState();
        com.mahghuuuls.statusworks.core.rule.PoolState pool = com.mahghuuuls.statusworks.core.rule.PoolMath.create(p);
        state.putPool("t:pool", 0, pool);
        com.mahghuuuls.statusworks.core.rule.PoolMath.advance(p, pool, 30);
        com.mahghuuuls.statusworks.core.rule.PoolMath.setPaused(pool, true);
        com.mahghuuuls.statusworks.core.rule.PoolMath.advance(p, pool, 10);
        NBTTagCompound nbt = codec.write(state);

        EntityState restored = new EntityState();
        codec.read(restored, nbt);
        com.mahghuuuls.statusworks.core.rule.PoolState r = restored.pool("t:pool");
        assertEquals(7, r.amount(), 1e-9);
        assertEquals(20, r.capacity(), 1e-9);
        assertEquals(40, r.age());
        assertEquals(30, r.progressAge());
        assertTrue(r.paused());
        assertEquals(360, com.mahghuuuls.statusworks.core.rule.PoolMath.remainingLifetime(r));
        assertEquals(70, com.mahghuuuls.statusworks.core.rule.PoolMath.remainingToDeadline(p, r));
        assertTrue(restored.takeLoadNotes().isEmpty());

        nbt.removeTag("pools");
        nbt.setInteger("v", 1);
        EntityState old = new EntityState();
        codec.read(old, nbt);
        assertTrue(old.isEmpty());
    }

    @Test
    void aLoweredDefinitionCapacityReachesASavedPool() {
        DefinitionRegistry before = new DefinitionRegistry();
        com.mahghuuuls.statusworks.api.definition.PoolDefinition wide = com.mahghuuuls.statusworks.api.definition
                .PoolDefinition.builder("t:mana").capacity(100).startAmount(80).build();
        before.registerPool(wide, "test");
        before.freeze();
        EntityState state = new EntityState();
        state.putPool("t:mana", 0, com.mahghuuuls.statusworks.core.rule.PoolMath.create(wide));
        NBTTagCompound nbt = new EntityStateCodec(before, (id, why) -> { }).write(state);

        DefinitionRegistry after = new DefinitionRegistry();
        after.registerPool(com.mahghuuuls.statusworks.api.definition.PoolDefinition.builder("t:mana")
                .capacity(50).build(), "test");
        after.freeze();
        EntityState restored = new EntityState();
        new EntityStateCodec(after, (id, why) -> { }).read(restored, nbt);
        assertEquals(50, restored.pool("t:mana").capacity(), 1e-9);
        assertEquals(50, restored.pool("t:mana").amount(), 1e-9);
        EntityState.LoadNote note = restored.takeLoadNotes().get(0);
        assertEquals(80, note.countBefore, 1e-9);
        assertEquals(50, note.countAfter, 1e-9);
    }

    @Test
    void aSavedPoolWithoutItsDefinitionEndsInvalid() {
        DefinitionRegistry before = new DefinitionRegistry();
        com.mahghuuuls.statusworks.api.definition.PoolDefinition p = com.mahghuuuls.statusworks.api.definition
                .PoolDefinition.builder("t:gone").capacity(10).startAmount(4).build();
        before.registerPool(p, "test");
        before.freeze();
        EntityState state = new EntityState();
        state.putPool("t:gone", 0, com.mahghuuuls.statusworks.core.rule.PoolMath.create(p));
        NBTTagCompound nbt = new EntityStateCodec(before, (id, why) -> { }).write(state);

        DefinitionRegistry after = new DefinitionRegistry();
        after.freeze();
        EntityState restored = new EntityState();
        new EntityStateCodec(after, (id, why) -> { }).read(restored, nbt);
        assertNull(restored.pool("t:gone"));
        EntityState.LoadNote note = restored.takeLoadNotes().get(0);
        assertEquals("pool", note.kind);
        assertEquals(4, note.countBefore, 1e-9);
        assertEquals("invalid", note.reason);
    }

    @Test
    void runningCooldownsRoundTripAndARemovedOneEndsInvalid() {
        DefinitionRegistry registry = new DefinitionRegistry();
        registry.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("t:dash")
                .build(), "test");
        registry.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("t:old")
                .build(), "test");
        registry.freeze();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> { });
        EntityState state = new EntityState();
        com.mahghuuuls.statusworks.core.rule.CooldownState dash = com.mahghuuuls.statusworks.core.rule.CooldownRules
                .start(300);
        for (int i = 0; i < 60; i++) {
            com.mahghuuuls.statusworks.core.rule.CooldownRules.tick(dash);
        }
        state.putCooldown("t:dash", 0, dash);
        state.putCooldown("t:old", 1, com.mahghuuuls.statusworks.core.rule.CooldownRules.start(50));
        NBTTagCompound nbt = codec.write(state);

        EntityState restored = new EntityState();
        codec.read(restored, nbt);
        assertEquals(240, com.mahghuuuls.statusworks.core.rule.CooldownRules.remaining(restored.cooldown("t:dash")));

        DefinitionRegistry after = new DefinitionRegistry();
        after.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("t:dash")
                .build(), "test");
        after.freeze();
        EntityState trimmed = new EntityState();
        new EntityStateCodec(after, (id, why) -> { }).read(trimmed, nbt);
        assertNull(trimmed.cooldown("t:old"));
        EntityState.LoadNote note = trimmed.takeLoadNotes().get(0);
        assertEquals("cooldown", note.kind);
        assertEquals("invalid", note.reason);
    }

    @Test
    void theContributionRecordIsSavedEvenWithoutInstances() {
        DefinitionRegistry registry = new DefinitionRegistry();
        registry.freeze();
        EntityStateCodec codec = new EntityStateCodec(registry, (id, why) -> { });
        EntityState state = new EntityState();
        state.contributions().restore("status:a:hp#0", new com.mahghuuuls.statusworks.core.contribution.Applied(
                true, "generic.maxHealth", 0, 10));
        state.contributions().restore("status:a:pow#0", new com.mahghuuuls.statusworks.core.contribution.Applied(
                false, "t:power", -1, 3));
        NBTTagCompound nbt = codec.write(state);
        EntityState restored = new EntityState();
        codec.read(restored, nbt);
        assertEquals(state.contributions().entries(), restored.contributions().entries());
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
