package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.ParticleAudience;
import com.mahghuuuls.statusworks.api.definition.Particles;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleTableTest {

    private static Presentation particles(Particles p) {
        return Presentation.builder().particles(p).build();
    }

    private static PresentationTable table() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("t:red").presentation(particles(Particles.builder("mobSpell")
                .color(255, 0, 0).perSecond(4).build())).build(), "test");
        r.registerStatus(StatusDefinition.builder("t:plain").build(), "test");
        r.registerStatus(StatusDefinition.builder("t:secret").presentation(Presentation.builder().hidden()
                .particles(Particles.builder("flame").build()).build()).build(), "test");
        r.registerPool(PoolDefinition.builder("t:far").capacity(10).presentation(particles(Particles
                .builder("reddust").longDistance().build())).build(), "test");
        r.registerMark(MarkDefinition.builder("t:quiet").presentation(Presentation.builder().markHud(false, false)
                .particles(Particles.builder("mobSpellAmbient").build()).build()).build(), "test");
        r.registerMark(MarkDefinition.builder("t:both").presentation(particles(Particles.builder("heart")
                .audience(ParticleAudience.BOTH).build())).build(), "test");
        r.registerMark(MarkDefinition.builder("t:nohud").presentation(Presentation.builder().markHud(false, false)
                .build()).build(), "test");
        assertTrue(r.freeze().isEmpty());
        return PresentationTable.build(r);
    }

    @Test
    void rowsCarryParticlesAndTheServerKeepsTheAudience() {
        PresentationTable t = table();
        HudDefinition red = t.status("t:red");
        assertEquals("mobSpell", red.particleType);
        assertEquals(0xFF0000, red.particleColor);
        assertEquals(4f, red.particlesPerSecond, 1e-6);
        assertFalse(red.particlesFar);
        assertEquals(ParticleAudience.NEARBY, t.audience(red.index));
        assertNull(t.status("t:plain").particleType);
        assertNull(t.audience(t.status("t:plain").index));
        assertTrue(t.pool("t:far").particlesFar);
        assertTrue(t.hasParticles());
    }

    @Test
    void aHiddenDefinitionShowsNoParticlesAndAParticleMarkHasARowWithoutAHudEntry() {
        PresentationTable t = table();
        assertNull(t.status("t:secret"), "hidden: no row, so nothing is ever sent");
        HudDefinition quiet = t.markParticles("t:quiet");
        assertNotNull(quiet, "a mark on no HUD still has its particles");
        assertNull(t.mark("t:quiet", true));
        assertNull(t.mark("t:quiet", false));
        assertEquals(ParticleAudience.SOURCE, t.audience(quiet.index), "a mark's default audience");
        assertEquals(ParticleAudience.BOTH, t.audience(t.markParticles("t:both").index));
        assertNull(t.markParticles("t:nohud"), "no HUD and no particles: no row");
    }

    @Test
    void particleSettingsRoundTripThroughBytes() {
        PresentationTable t = table();
        ByteBuf buf = Unpooled.buffer();
        new TableMessage(t.rows()).toBytes(buf);
        TableMessage back = new TableMessage();
        back.fromBytes(buf);
        assertEquals(t.rows().size(), back.rows.size());
        for (int i = 0; i < t.rows().size(); i++) {
            HudDefinition a = t.rows().get(i);
            HudDefinition b = back.rows.get(i);
            assertEquals(a.particleType, b.particleType);
            assertEquals(a.particleColor, b.particleColor);
            assertEquals(a.particlesPerSecond, b.particlesPerSecond, 1e-6);
            assertEquals(a.particlesFar, b.particlesFar);
        }
        ByteBuf buf2 = Unpooled.buffer();
        HudEntry e = new HudEntry("0|", 0, HudEntry.OWN, 2, 0, 0, 80, true, null,
                java.util.Collections.<HudEntry.Group>emptyList());
        new AudienceEntriesMessage(42, true, java.util.Collections.singletonList(e),
                java.util.Collections.<String>emptyList()).toBytes(buf2);
        AudienceEntriesMessage m = new AudienceEntriesMessage();
        m.fromBytes(buf2);
        assertEquals(42, m.entity);
        assertTrue(m.snapshot);
        assertEquals(2, m.changed.get(0).count);
    }
}
