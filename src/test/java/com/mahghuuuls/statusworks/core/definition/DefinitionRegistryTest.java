package com.mahghuuuls.statusworks.core.definition;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.definition.Direction;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryTest {

    private static String poolError(PoolDefinition definition) {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerPool(definition, "moda");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertNull(r.pool(definition.id()));
        assertTrue(r.isInvalidPool(definition.id()));
        return errors.get(0).reason;
    }

    @Test
    void poolsHaveTheirOwnIdsOrderAndValidation() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:same").build(), "moda");
        r.registerPool(PoolDefinition.builder("a:same").capacity(5).build(), "moda");
        r.registerPool(PoolDefinition.builder("a:second").capacity(5).build(), "moda");
        assertTrue(r.freeze().isEmpty());
        assertNotNull(r.status("a:same"));
        assertEquals(0, r.pool("a:same").order());
        assertEquals(1, r.pool("a:second").order());
        assertFalse(r.registerPool(PoolDefinition.builder("a:late").build(), "moda").isAccepted());
    }

    @Test
    void invalidPoolsAreDisabledWithTheirReason() {
        assertEquals("bad-capacity", poolError(PoolDefinition.builder("a:p").capacity(0).build()));
        assertEquals("unknown-mode", poolError(PoolDefinition.builder("a:p").start("half").build()));
        assertEquals("bad-lifetime", poolError(PoolDefinition.builder("a:p").lifetime(0).build()));
        assertEquals("unknown-mode", poolError(PoolDefinition.builder("a:p").progression("decay", "fill", 1).build()));
        assertEquals("unknown-mode", poolError(PoolDefinition.builder("a:p").progression("fixed", "up", 1).build()));
        assertEquals("bad-rate", poolError(PoolDefinition.builder("a:p").fixed(Direction.FILL, 0).build()));
        assertEquals("bad-rate", poolError(PoolDefinition.builder("a:p").percentOfCurrent(Direction.DRAIN, 150)
                .build()));
        assertEquals("bad-duration", poolError(PoolDefinition.builder("a:p").toTarget(0, 0).build()));
        assertEquals("bad-cutoff", poolError(PoolDefinition.builder("a:p").cutoff(-3).build()));
        assertEquals("unknown-mode", poolError(PoolDefinition.builder("a:p").deathRule("maybe").build()));
    }

    @Test
    void cooldownsAreValidatedInTheirOwnTable() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("a:dash").build(),
                "moda");
        r.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("a:bad")
                .defaultDuration(0).build(), "moda");
        r.registerCooldown(com.mahghuuuls.statusworks.api.definition.CooldownDefinition.builder("a:odd")
                .deathRule("sometimes").build(), "moda");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertNotNull(r.cooldown("a:dash"));
        assertEquals(2, errors.size());
        assertEquals("bad-duration", errors.get(0).reason);
        assertEquals("unknown-mode", errors.get(1).reason);
        assertTrue(r.isInvalidCooldown("a:bad"));
    }

    @Test
    void contributionsAreValidated() {
        DefinitionRegistry r = new DefinitionRegistry("t:power"::equals);
        r.registerStatus(StatusDefinition.builder("a:ok").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("generic.maxHealth", "add", "per_stack", 4)).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:typed").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.type("t:power", "per_stack", 1)).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:typo").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("generic.maxHelth", "add", "fixed", 4)).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:notype").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.type("t:nope", "fixed", 1)).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:op").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("generic.armor", "divide", "fixed", 1)).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:form").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("generic.armor", "add", "per_fill", 1)).build(), "moda");
        r.registerPool(PoolDefinition.builder("a:pform").capacity(5).contribute(com.mahghuuuls.statusworks.api
                .definition.Contribution.attribute("generic.armor", "add", "per_stack", 1)).build(), "moda");
        r.registerPool(PoolDefinition.builder("a:step").capacity(5).updateStep(0).build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:modded").contribute(com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("somemod.mana", "add", "fixed", 1)).build(), "moda");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertNotNull(r.status("a:ok"));
        assertNotNull(r.status("a:typed"));
        assertNotNull(r.status("a:modded"), "another mod's attribute waits for the server start");
        java.util.Map<String, String> reasons = new java.util.LinkedHashMap<>();
        errors.forEach(e -> reasons.put(e.id, e.reason));
        assertEquals("unknown-attribute", reasons.get("a:typo"));
        assertEquals("unknown-type", reasons.get("a:notype"));
        assertEquals("unknown-mode", reasons.get("a:op"));
        assertEquals("bad-form", reasons.get("a:form"));
        assertEquals("bad-form", reasons.get("a:pform"));
        assertEquals("bad-step", reasons.get("a:step"));

        List<DefinitionRegistry.Error> late = r.checkLateAttributes("othermod.power"::equals);
        assertEquals(1, late.size());
        assertEquals("a:modded", late.get(0).id);
        assertNull(r.status("a:modded"));
        assertTrue(r.isInvalid("a:modded"));
        assertNotNull(r.status("a:ok"), "vanilla attributes are always known");
    }

    @Test
    void marksAreValidatedInTheirOwnTable() {
        DefinitionRegistry r = new DefinitionRegistry();
        com.mahghuuuls.statusworks.api.definition.Contribution armor = com.mahghuuuls.statusworks.api.definition
                .Contribution.attribute("generic.armor", "add", "fixed", 2);
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:hunt").targetLimit(3)
                .contribute(com.mahghuuuls.statusworks.api.definition.MarkSide.SOURCE, armor).build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:free").unlimitedTargets()
                .noDuration().build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:zero").targetLimit(0)
                .build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:odd").overflow("drop")
                .build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:stack")
                .contribute(com.mahghuuuls.statusworks.api.definition.MarkSide.TARGET,
                        com.mahghuuuls.statusworks.api.definition.Contribution.attribute("generic.armor", "add",
                                "per_stack", 1)).build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:far").maximumDistance(-1)
                .maximumInactive(-5).build(), "moda");
        r.registerMark(com.mahghuuuls.statusworks.api.definition.MarkDefinition.builder("a:modded")
                .contribute(com.mahghuuuls.statusworks.api.definition.MarkSide.TARGET,
                        com.mahghuuuls.statusworks.api.definition.Contribution.attribute("othermod.power", "add",
                                "fixed", 1)).build(), "moda");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertNotNull(r.mark("a:hunt"));
        assertNotNull(r.mark("a:free"));
        assertEquals("bad-limit", errors.get(0).reason);
        assertEquals("unknown-mode", errors.get(1).reason);
        assertEquals("bad-form", errors.get(2).reason, "a mark contribution is fixed");
        assertEquals("bad-distance", errors.get(3).reason);
        assertEquals("bad-inactive", errors.get(4).reason);
        assertEquals(5, errors.size());
        assertTrue(r.isInvalidMark("a:zero"));
        assertNull(r.status("a:hunt"), "marks have their own ids");
        List<DefinitionRegistry.Error> late = r.checkLateAttributes(id -> false);
        assertEquals(1, late.size());
        assertNull(r.mark("a:modded"));
        assertTrue(r.isInvalidMark("a:modded"));
    }

    @Test
    void aDuplicatePoolIdKeepsTheFirst() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerPool(PoolDefinition.builder("a:p").capacity(3).build(), "moda");
        r.registerPool(PoolDefinition.builder("a:p").capacity(9).build(), "modb");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals("duplicate-id", errors.get(0).reason);
        assertEquals(3, r.pool("a:p").definition().capacity(), 1e-9);
    }

    @Test
    void validDefinitionsKeepRegistrationOrder() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:one").build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:two").build(), "moda");
        assertTrue(r.freeze().isEmpty());
        assertEquals(0, r.status("a:one").order());
        assertEquals(1, r.status("a:two").order());
        assertEquals("moda", r.status("a:one").origin());
    }

    @Test
    void invalidDefinitionIsDisabledAndOthersWork() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:good").build(), "moda");
        r.registerStatus(StatusDefinition.builder("a:bad").limit(0).build(), "scripts/a.zs");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals(1, errors.size());
        assertEquals("a:bad", errors.get(0).id);
        assertEquals("bad-limit", errors.get(0).reason);
        assertEquals("scripts/a.zs", errors.get(0).origin);
        assertTrue(errors.get(0).toString().contains("scripts/a.zs"));
        assertNotNull(r.status("a:good"));
        assertNull(r.status("a:bad"));
        assertTrue(r.isInvalid("a:bad"));
    }

    @Test
    void unknownModeTextIsInvalid() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:s").expiration("sometimes").build(), "m");
        r.registerStatus(StatusDefinition.builder("a:g").expiration("gradual").build(), "m");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals(1, errors.size());
        assertEquals("unknown-mode", errors.get(0).reason);
        assertEquals(ExpirationMode.GRADUAL, r.status("a:g").definition().expiration());
    }

    @Test
    void everyRuleIsChecked() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("Bad Id").build(), "m");
        r.registerStatus(StatusDefinition.builder("a:d").defaultDuration(0).build(), "m");
        r.registerStatus(StatusDefinition.builder("a:n").expiration(ExpirationMode.NONE).defaultDuration(0).build(), "m");
        r.registerStatus(StatusDefinition.builder("a:m").maximumRemaining(0).build(), "m");
        r.registerStatus(StatusDefinition.builder("a:r").refresh("forever").build(), "m");
        r.registerStatus(StatusDefinition.builder("a:o").ownership("mine").build(), "m");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals(5, errors.size());
        assertNotNull(r.status("a:n"), "a none-expiration status needs no duration");
    }

    @Test
    void firstRegistrationOfADuplicateWins() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:x").limit(2).build(), "first");
        r.registerStatus(StatusDefinition.builder("a:x").limit(3).build(), "second");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals(1, errors.size());
        assertEquals("duplicate-id", errors.get(0).reason);
        assertEquals("second", errors.get(0).origin);
        assertEquals(2, r.status("a:x").definition().limit());
        assertFalse(r.isInvalid("a:x"));
    }

    @Test
    void plainIdsAreValidAndWhitespaceIsNot() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("rage").build(), "m");
        r.registerStatus(StatusDefinition.builder("Two Words").build(), "m");
        r.registerStatus(StatusDefinition.builder("").build(), "m");
        assertEquals(2, r.freeze().size());
        assertNotNull(r.status("rage"));
    }

    @Test
    void theFirstRegistrationClaimsTheIdEvenWhenInvalid() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.registerStatus(StatusDefinition.builder("a:x").limit(0).build(), "first");
        r.registerStatus(StatusDefinition.builder("a:x").limit(3).build(), "second");
        List<DefinitionRegistry.Error> errors = r.freeze();
        assertEquals(2, errors.size());
        assertEquals("bad-limit", errors.get(0).reason);
        assertEquals("duplicate-id", errors.get(1).reason);
        assertEquals("second", errors.get(1).origin);
        assertNull(r.status("a:x"));
        assertTrue(r.isInvalid("a:x"));
        assertEquals("first", r.invalid().get("a:x"));
    }

    @Test
    void registrationAfterFreezeIsRefused() {
        DefinitionRegistry r = new DefinitionRegistry();
        r.freeze();
        RegistrationResult result = r.registerStatus(StatusDefinition.builder("a:late").build(), "late");
        assertFalse(result.isAccepted());
        assertTrue(result.error().contains("frozen"));
        assertNull(r.status("a:late"));
    }
}
