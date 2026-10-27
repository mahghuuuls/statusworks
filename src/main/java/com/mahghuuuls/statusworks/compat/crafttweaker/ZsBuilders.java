package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * The definition builders of the scripts, one per kind, each forwarding to the Java builder. Modes, rules, value forms,
 * and sides are text; an unknown text makes the definition invalid when definitions freeze, reported with its script
 * file. Each callback has one setter. {@code register()} hands the definition to Statusworks and returns whether it was
 * accepted for validation.
 */
public final class ZsBuilders {

    private ZsBuilders() {
    }

    private static boolean accepted(RegistrationResult result) {
        return result.isAccepted();
    }

    @ZenRegister
    @ZenClass("mods.statusworks.StatusBuilder")
    public static final class Status {
        private final StatusDefinition.Builder b;

        Status(String id) {
            this.b = StatusDefinition.builder(id);
        }

        @ZenMethod
        public Status limit(int limit) {
            b.limit(limit);
            return this;
        }

        @ZenMethod
        public Status defaultDuration(int ticks) {
            b.defaultDuration(ticks);
            return this;
        }

        /** {@code shared}, {@code gradual}, {@code independent}, or {@code none}. */
        @ZenMethod
        public Status expiration(String mode) {
            b.expiration(mode);
            return this;
        }

        /** {@code reset}, {@code extend}, or {@code preserve}. */
        @ZenMethod
        public Status refresh(String rule) {
            b.refresh(rule);
            return this;
        }

        @ZenMethod
        public Status maximumRemaining(int ticks) {
            b.maximumRemaining(ticks);
            return this;
        }

        @ZenMethod
        public Status refreshOnReject(boolean refresh) {
            b.refreshOnReject(refresh);
            return this;
        }

        /** {@code shared} or {@code per_source}. */
        @ZenMethod
        public Status ownership(String ownership) {
            b.ownership(ownership);
            return this;
        }

        /** {@code clear} or {@code keep}. */
        @ZenMethod
        public Status deathRule(String rule) {
            b.deathRule(rule);
            return this;
        }

        @ZenMethod
        public Status onApplied(ZsFunctions.Status f) {
            b.onApplied(c -> f.handle(new ZsContexts.Status(c)));
            return this;
        }

        @ZenMethod
        public Status onQuantityChanged(ZsFunctions.Status f) {
            b.onQuantityChanged(c -> f.handle(new ZsContexts.Status(c)));
            return this;
        }

        @ZenMethod
        public Status onRemoved(ZsFunctions.Status f) {
            b.onRemoved(c -> f.handle(new ZsContexts.Status(c)));
            return this;
        }

        @ZenMethod
        public Status onInterval(int ticks, ZsFunctions.Status f) {
            b.onInterval(ticks, c -> f.handle(new ZsContexts.Status(c)));
            return this;
        }

        /**
         * An attribute contribution: an attribute id, an operation ({@code add}, {@code multiply_base},
         * {@code multiply_total}), a value form ({@code fixed}, {@code per_stack}), and the value.
         */
        @ZenMethod
        public Status contributeAttribute(String attribute, String operation, String form, double value) {
            b.contribute(Contribution.attribute(attribute, operation, form, value));
            return this;
        }

        /** A contribution to an addon's contribution type, by its id. */
        @ZenMethod
        public Status contributeType(String typeId, String form, double value) {
            b.contribute(Contribution.type(typeId, form, value));
            return this;
        }

        @ZenMethod
        public Status presentation(ZsPresentation p) {
            b.presentation(p.build());
            return this;
        }

        @ZenMethod
        public boolean register() {
            return accepted(StatusworksApi.registerStatus(b.build()));
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.PoolBuilder")
    public static final class Pool {
        private final PoolDefinition.Builder b;

        Pool(String id) {
            this.b = PoolDefinition.builder(id);
        }

        @ZenMethod
        public Pool capacity(double capacity) {
            b.capacity(capacity);
            return this;
        }

        /** {@code empty}, {@code full}, or {@code amount} (with {@code startAmount}). */
        @ZenMethod
        public Pool start(String start) {
            b.start(start);
            return this;
        }

        @ZenMethod
        public Pool startAmount(double amount) {
            b.startAmount(amount);
            return this;
        }

        @ZenMethod
        public Pool lifetime(int ticks) {
            b.lifetime(ticks);
            return this;
        }

        /**
         * A progression: a rule ({@code fixed}, {@code percent_of_capacity}, {@code percent_of_current}), a direction
         * ({@code fill} or {@code drain}), and the rate per second.
         */
        @ZenMethod
        public Pool progression(String rule, String direction, double ratePerSecond) {
            b.progression(rule, direction, ratePerSecond);
            return this;
        }

        @ZenMethod
        public Pool toTarget(double target, int ticks) {
            b.toTarget(target, ticks);
            return this;
        }

        @ZenMethod
        public Pool cutoff(double amount) {
            b.cutoff(amount);
            return this;
        }

        @ZenMethod
        public Pool deathRule(String rule) {
            b.deathRule(rule);
            return this;
        }

        @ZenMethod
        public Pool onAmountChanged(ZsFunctions.Pool f) {
            b.onAmountChanged(c -> f.handle(new ZsContexts.Pool(c)));
            return this;
        }

        @ZenMethod
        public Pool onEmpty(ZsFunctions.Pool f) {
            b.onEmpty(c -> f.handle(new ZsContexts.Pool(c)));
            return this;
        }

        @ZenMethod
        public Pool onFull(ZsFunctions.Pool f) {
            b.onFull(c -> f.handle(new ZsContexts.Pool(c)));
            return this;
        }

        @ZenMethod
        public Pool onTargetReached(ZsFunctions.Pool f) {
            b.onTargetReached(c -> f.handle(new ZsContexts.Pool(c)));
            return this;
        }

        @ZenMethod
        public Pool onRemoved(ZsFunctions.Pool f) {
            b.onRemoved(c -> f.handle(new ZsContexts.Pool(c)));
            return this;
        }

        /** Value forms: {@code fixed}, {@code per_amount}, {@code per_fill}. */
        @ZenMethod
        public Pool contributeAttribute(String attribute, String operation, String form, double value) {
            b.contribute(Contribution.attribute(attribute, operation, form, value));
            return this;
        }

        @ZenMethod
        public Pool contributeType(String typeId, String form, double value) {
            b.contribute(Contribution.type(typeId, form, value));
            return this;
        }

        @ZenMethod
        public Pool updateStep(double percentOfCapacity) {
            b.updateStep(percentOfCapacity);
            return this;
        }

        @ZenMethod
        public Pool presentation(ZsPresentation p) {
            b.presentation(p.build());
            return this;
        }

        @ZenMethod
        public boolean register() {
            return accepted(StatusworksApi.registerPool(b.build()));
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.MarkBuilder")
    public static final class Mark {
        private final MarkDefinition.Builder b;

        Mark(String id) {
            this.b = MarkDefinition.builder(id);
        }

        @ZenMethod
        public Mark targetLimit(int targets) {
            b.targetLimit(targets);
            return this;
        }

        @ZenMethod
        public Mark unlimitedTargets() {
            b.unlimitedTargets();
            return this;
        }

        /** {@code replace_oldest} or {@code reject}. */
        @ZenMethod
        public Mark overflow(String rule) {
            b.overflow(rule);
            return this;
        }

        @ZenMethod
        public Mark refresh(String rule) {
            b.refresh(rule);
            return this;
        }

        @ZenMethod
        public Mark maximumRemaining(int ticks) {
            b.maximumRemaining(ticks);
            return this;
        }

        @ZenMethod
        public Mark defaultDuration(int ticks) {
            b.defaultDuration(ticks);
            return this;
        }

        @ZenMethod
        public Mark noDuration() {
            b.noDuration();
            return this;
        }

        @ZenMethod
        public Mark keepOnSourceDeath(boolean keep) {
            b.keepOnSourceDeath(keep);
            return this;
        }

        @ZenMethod
        public Mark keepOnTargetDeath(boolean keep) {
            b.keepOnTargetDeath(keep);
            return this;
        }

        @ZenMethod
        public Mark maximumDistance(double blocks) {
            b.maximumDistance(blocks);
            return this;
        }

        @ZenMethod
        public Mark maximumInactive(int ticks) {
            b.maximumInactive(ticks);
            return this;
        }

        @ZenMethod
        public Mark onInterval(int ticks, ZsFunctions.Mark f) {
            b.onInterval(ticks, c -> f.handle(new ZsContexts.Mark(c)));
            return this;
        }

        @ZenMethod
        public Mark onApplied(ZsFunctions.Mark f) {
            b.onApplied(c -> f.handle(new ZsContexts.Mark(c)));
            return this;
        }

        @ZenMethod
        public Mark onInactive(ZsFunctions.Mark f) {
            b.onInactive(c -> f.handle(new ZsContexts.Mark(c)));
            return this;
        }

        @ZenMethod
        public Mark onActive(ZsFunctions.Mark f) {
            b.onActive(c -> f.handle(new ZsContexts.Mark(c)));
            return this;
        }

        @ZenMethod
        public Mark onRemoved(ZsFunctions.Mark f) {
            b.onRemoved(c -> f.handle(new ZsContexts.Mark(c)));
            return this;
        }

        /** A contribution on one side ({@code source} or {@code target}); marks take the {@code fixed} form only. */
        @ZenMethod
        public Mark contributeAttribute(String side, String attribute, String operation, String form, double value) {
            b.contribute(side, Contribution.attribute(attribute, operation, form, value));
            return this;
        }

        @ZenMethod
        public Mark contributeType(String side, String typeId, String form, double value) {
            b.contribute(side, Contribution.type(typeId, form, value));
            return this;
        }

        @ZenMethod
        public Mark presentation(ZsPresentation p) {
            b.presentation(p.build());
            return this;
        }

        @ZenMethod
        public boolean register() {
            return accepted(StatusworksApi.registerMark(b.build()));
        }
    }

    @ZenRegister
    @ZenClass("mods.statusworks.CooldownBuilder")
    public static final class Cooldown {
        private final CooldownDefinition.Builder b;

        Cooldown(String id) {
            this.b = CooldownDefinition.builder(id);
        }

        @ZenMethod
        public Cooldown defaultDuration(int ticks) {
            b.defaultDuration(ticks);
            return this;
        }

        @ZenMethod
        public Cooldown deathRule(String rule) {
            b.deathRule(rule);
            return this;
        }

        /** Cooldowns carry no contributions: this makes the definition invalid ("cooldowns cannot have contributions"). */
        @ZenMethod
        public Cooldown contributeAttribute(String attribute, String operation, String form, double value) {
            b.contribute(Contribution.attribute(attribute, operation, form, value));
            return this;
        }

        /** Cooldowns carry no contributions: this makes the definition invalid. */
        @ZenMethod
        public Cooldown contributeType(String typeId, String form, double value) {
            b.contribute(Contribution.type(typeId, form, value));
            return this;
        }

        @ZenMethod
        public Cooldown onEnded(ZsFunctions.Cooldown f) {
            b.onEnded(c -> f.handle(new ZsContexts.Cooldown(c)));
            return this;
        }

        @ZenMethod
        public Cooldown presentation(ZsPresentation p) {
            b.presentation(p.build());
            return this;
        }

        @ZenMethod
        public boolean register() {
            return accepted(StatusworksApi.registerCooldown(b.build()));
        }
    }
}
