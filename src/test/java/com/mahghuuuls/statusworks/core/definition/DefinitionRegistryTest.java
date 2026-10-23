package com.mahghuuuls.statusworks.core.definition;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionRegistryTest {

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
