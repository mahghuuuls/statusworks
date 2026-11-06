package com.mahghuuuls.statusworks.network;

import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarmfulTableTest {

    private static PresentationTable table() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("t:plain").build(), "test");
        r.registerStatus(StatusDefinition.builder("t:bleed").presentation(Presentation.builder().harmful().build())
                .build(), "test");
        r.registerPool(PoolDefinition.builder("t:drain").capacity(10).presentation(Presentation.builder().harmful()
                .build()).build(), "test");
        r.registerCooldown(CooldownDefinition.builder("t:dash").build(), "test");
        r.registerMark(MarkDefinition.builder("t:hunt").build(), "test");
        r.registerMark(MarkDefinition.builder("t:curse").presentation(Presentation.builder().harmful().build())
                .build(), "test");
        r.registerMark(MarkDefinition.builder("t:heal").presentation(Presentation.builder()
                .markHarmful(false, false).build()).build(), "test");
        // Every other flag off, and particles after the flags byte, so a decode that mixes the bits shows.
        r.registerStatus(StatusDefinition.builder("t:quiet").presentation(Presentation.builder().harmful()
                .showCount(false).showAmount(false).showTime(false).particles(Particles.builder("mobSpell")
                        .color(1, 2, 3).perSecond(2).longDistance().build()).build()).build(), "test");
        r.registerStatus(StatusDefinition.builder("t:shown").presentation(Presentation.builder().showCount(false)
                .build()).build(), "test");
        assertTrue(r.freeze().isEmpty());
        return PresentationTable.build(r);
    }

    @Test
    void aDefinitionWithoutTheOptionIsBeneficialAndAMarkIsHarmfulOnlyOnTheTargetSide() {
        Presentation p = Presentation.builder().build();
        assertFalse(p.harmful());
        assertFalse(p.markHarmfulOnSource());
        assertTrue(p.markHarmfulOnTarget());
        assertFalse(Presentation.DEFAULT.harmful());
        assertTrue(Presentation.DEFAULT.markHarmfulOnTarget());
    }

    @Test
    void harmfulMakesEverySideHarmfulAndMarkHarmfulSetsEachSide() {
        Presentation all = Presentation.builder().markHarmful(false, false).harmful().build();
        assertTrue(all.harmful());
        assertTrue(all.markHarmfulOnTarget());
        Presentation sourceOnly = Presentation.builder().harmful().markHarmful(true, false).build();
        assertTrue(sourceOnly.harmful());
        assertTrue(sourceOnly.markHarmfulOnSource());
        assertFalse(sourceOnly.markHarmfulOnTarget());
    }

    @Test
    void rowsCarryTheValues() {
        PresentationTable t = table();
        assertFalse(t.status("t:plain").harmful);
        assertTrue(t.status("t:bleed").harmful);
        assertTrue(t.pool("t:drain").harmful);
        assertFalse(t.cooldown("t:dash").harmful);
        HudDefinition hunt = t.mark("t:hunt", true);
        assertFalse(hunt.harmful);
        assertTrue(hunt.harmfulOnTarget);
        HudDefinition curse = t.mark("t:curse", true);
        assertTrue(curse.harmful);
        assertTrue(curse.harmfulOnTarget);
        HudDefinition heal = t.mark("t:heal", true);
        assertFalse(heal.harmful);
        assertFalse(heal.harmfulOnTarget);
    }

    @Test
    void theValuesSurviveTheWireAndLeaveTheOtherFlagsAlone() {
        for (HudDefinition row : table().rows()) {
            ByteBuf buf = Unpooled.buffer();
            row.write(buf);
            HudDefinition back = HudDefinition.read(buf);
            assertEquals(0, buf.readableBytes(), row.id);
            assertEquals(row.harmful, back.harmful, row.id);
            assertEquals(row.harmfulOnTarget, back.harmfulOnTarget, row.id);
            assertEquals(row.showCount, back.showCount, row.id);
            assertEquals(row.showAmount, back.showAmount, row.id);
            assertEquals(row.showTime, back.showTime, row.id);
            assertEquals(row.particleType, back.particleType, row.id);
            assertEquals(row.particleColor, back.particleColor, row.id);
            assertEquals(row.particlesFar, back.particlesFar, row.id);
        }
        HudDefinition quiet = table().status("t:quiet");
        assertTrue(quiet.harmful);
        assertFalse(quiet.showCount || quiet.showAmount || quiet.showTime);
        assertFalse(table().status("t:shown").harmful);
    }
}
