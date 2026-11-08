package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.GroupAvailability;
import com.mahghuuuls.statusworks.core.diagnostic.ErrorCounts;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBoundaryTest {

    private static final String T = "test:grouped";
    private static final String B = "test:other";

    /** A scripted game side: entities are strings; invalidation answers come from a script per type. */
    private final class FakeSide implements GroupBoundary.Side<String> {
        final List<String> groupTypes = new ArrayList<>(Collections.singletonList(T));
        final Set<String> notApplying = new HashSet<>();
        boolean appliesThrows;
        final Map<String, GroupStates> states = new HashMap<>();
        /** Committed values by entity, then by type. */
        final Map<String, Map<String, Map<String, Double>>> committed = new HashMap<>();
        /** "yes", "no", or "throw"; empty means "yes". */
        final Map<String, Deque<String>> answers = new HashMap<>();
        final Map<String, Runnable> onSettled = new HashMap<>();
        boolean settledThrows;

        Deque<String> answers(String type) {
            return answers.computeIfAbsent(type, k -> new ArrayDeque<>());
        }

        @Override
        public List<String> groupTypes() {
            return groupTypes;
        }

        @Override
        public boolean applies(String entity, String typeId) {
            if (appliesThrows) {
                throw new IllegalStateException("test");
            }
            return !notApplying.contains(typeId);
        }

        @Override
        public boolean invalidate(String entity, String typeId) {
            calls.add("invalidate " + typeId + " " + entity);
            String a = answers(typeId).isEmpty() ? "yes" : answers(typeId).poll();
            if (a.equals("throw")) {
                throw new IllegalStateException("test");
            }
            return a.equals("yes");
        }

        @Override
        public void settled(String entity, String typeId, Map<String, Double> group) {
            calls.add("settled " + typeId + " " + entity + " " + group);
            readInSettled = boundary.read(entity, typeId);
            onSettled.getOrDefault(typeId, () -> { }).run();
            if (settledThrows) {
                throw new IllegalStateException("test");
            }
        }

        @Override
        public Map<String, Double> committed(String entity, String typeId) {
            return new LinkedHashMap<>(committed.getOrDefault(entity, Collections.emptyMap())
                    .getOrDefault(typeId, Collections.emptyMap()));
        }

        @Override
        public GroupStates states(String entity) {
            return states.get(entity);
        }

        @Override
        public String label(String entity) {
            return entity;
        }
    }

    private final List<String> calls = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean diagnosticOn = true;
    private final FakeSide side = new FakeSide();
    private final GroupBoundary<String> boundary = new GroupBoundary<>(side, new ErrorCounts(),
            (m, t) -> errors.add(m), warnings::add, () -> diagnosticOn, diagnostics::add);
    private ContributionGroup readInSettled;

    private void present(String entity) {
        side.states.putIfAbsent(entity, new GroupStates());
    }

    private void set(String entity, String type, Map<String, Double> values) {
        side.committed.computeIfAbsent(entity, k -> new HashMap<>()).put(type, new LinkedHashMap<>(values));
    }

    private void join(String entity) {
        boundary.join(entity, () -> { });
    }

    /** One update as the manager runs it: commit the new values of the touched type inside the boundary. */
    private void change(String entity, String type, Map<String, Double> after) {
        boundary.update(entity, () -> boundary.commit(entity, Collections.singleton(type),
                () -> set(entity, type, after)));
    }

    private static Map<String, Double> values(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        }
        return m;
    }

    private List<String> callsFor(String type) {
        return calls.stream().filter(c -> c.contains(" " + type + " ")).collect(Collectors.toList());
    }

    private GroupAvailability availability(String entity, String type) {
        return boundary.read(entity, type).availability();
    }

    @Test
    void beforeTheJoinNothingIsSentAndTheGroupIsUnavailable() {
        present("p");
        change("p", T, values("s#0", 2));
        assertTrue(calls.isEmpty());
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T));
    }

    @Test
    void anEntityWithoutStateIsUnavailableAndGetsNoSignals() {
        assertEquals(GroupAvailability.UNAVAILABLE, availability("client", T));
        join("client");
        assertTrue(calls.isEmpty());
    }

    @Test
    void aJoinWithNoContributionsGivesAnAvailableEmptyGroup() {
        present("p");
        join("p");
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {}"), calls);
        ContributionGroup g = boundary.read("p", T);
        assertTrue(g.isAvailable());
        assertTrue(g.values().isEmpty());
    }

    @Test
    void aChangeSendsAnInvalidationThenTheSettledGroup() {
        present("p");
        set("p", T, values("s#0", 1));
        join("p");
        calls.clear();
        change("p", T, values("s#0", 2));
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {s#0=2.0}"), calls);
        assertEquals(values("s#0", 2), boundary.read("p", T).values());
    }

    @Test
    void theLastWithdrawalSettlesAnEmptyGroup() {
        present("p");
        set("p", T, values("s#0", 1));
        join("p");
        calls.clear();
        change("p", T, values());
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {}"), calls);
        assertTrue(boundary.read("p", T).isAvailable());
    }

    @Test
    void aTypeWithoutGroupSignalsGetsNoSignals() {
        side.groupTypes.clear();
        present("p");
        join("p");
        change("p", T, values("s#0", 1));
        assertTrue(calls.isEmpty());
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T));
    }

    @Test
    void aRefusalAndAnErrorBothFailUntilAConfirmedInvalidationAndASettledGroup() {
        present("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 2));
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        assertTrue(boundary.read("p", T).values().isEmpty(), "a failed group shows no values");
        side.answers(T).add("throw");
        change("p", T, values("s#0", 3));
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        assertEquals(1, errors.size());
        calls.clear();
        change("p", T, values("s#0", 4));
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {s#0=4.0}"), calls);
        assertTrue(boundary.read("p", T).isAvailable());
    }

    @Test
    void theConsumerShapedCaseAccepts110Of120OnlyAfterRecovery() {
        // A consumer that keeps a saved 110 and a maximum of 120 plus its group, and accepts only an available group.
        double[] accepted = {-1, -1};
        Runnable tryAccept = () -> {
            ContributionGroup g = boundary.read("p", T);
            if (accepted[0] < 0 && g.isAvailable()) {
                double max = 120 + g.values().values().stream().mapToDouble(Double::doubleValue).sum();
                accepted[0] = Math.min(110, max);
                accepted[1] = max;
            }
        };
        present("p");
        set("p", T, values("curse#0", -20));
        join("p");
        side.answers(T).add("throw");
        change("p", T, values());
        tryAccept.run();
        assertEquals(-1, accepted[0], "nothing is accepted while the group is failed");
        boundary.retry("p", () -> { });
        tryAccept.run();
        assertEquals(110, accepted[0]);
        assertEquals(120, accepted[1]);
    }

    @Test
    void aChangeMadeFromASettledSignalRunsAsTheNextPairForEveryType() {
        side.groupTypes.add(B);
        PassSerializer<String> passes = new PassSerializer<>(e -> { });
        present("p");
        join("p");
        calls.clear();
        Runnable changeBoth = () -> boundary.update("p", () -> boundary.commit("p", Arrays.asList(T, B), () -> {
            set("p", T, values("s#0", 1));
            set("p", B, values("b#0", 1));
        }));
        Runnable changeB = () -> boundary.update("p", () -> boundary.commit("p", Collections.singleton(B),
                () -> set("p", B, values("b#0", 2))));
        boolean[] once = {false};
        side.onSettled.put(T, () -> {
            if (!once[0]) {
                once[0] = true;
                passes.request("p", changeB, changeB);
            }
        });
        passes.request("p", changeBoth, changeBoth);
        assertEquals(Arrays.asList("invalidate test:other p", "settled test:other p {b#0=1.0}",
                "invalidate test:other p", "settled test:other p {b#0=2.0}"), callsFor(B));
    }

    @Test
    void sixtyFailedRetriesEndWithOneWarningAndAChangeStartsANewSeries() {
        assertEquals(60, GroupBoundary.RETRY_LIMIT, "the shipped limit");
        present("p");
        join("p");
        for (int i = 0; i < 500; i++) {
            side.answers(T).add("no");
        }
        change("p", T, values("s#0", 1));
        int retries = 0;
        while (!boundary.withFailures().isEmpty()) {
            boundary.retry("p", () -> { });
            retries++;
        }
        assertEquals(60, retries);
        assertEquals(1, warnings.size());
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        change("p", T, values("s#0", 2));
        retries = 0;
        while (!boundary.withFailures().isEmpty()) {
            boundary.retry("p", () -> { });
            retries++;
        }
        assertEquals(60, retries, "a change starts a new series of 60");
        assertEquals(2, warnings.size());
    }

    @Test
    void failingTwiceThenSucceedingRecoversOnTheSecondRetry() {
        present("p");
        join("p");
        side.answers(T).addAll(Arrays.asList("no", "no", "yes"));
        change("p", T, values("s#0", 1));
        boundary.retry("p", () -> { });
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        boundary.retry("p", () -> { });
        assertTrue(boundary.read("p", T).isAvailable());
        assertTrue(boundary.withFailures().isEmpty());
    }

    @Test
    void aThrowingSettledSignalLeavesTheGroupAvailableWithItsValues() {
        present("p");
        set("p", T, values("s#0", 5));
        side.settledThrows = true;
        join("p");
        assertEquals(values("s#0", 5), boundary.read("p", T).values());
        assertTrue(boundary.read("p", T).isAvailable());
        assertEquals(1, errors.size());
    }

    @Test
    void theGroupIsAvailableWithTheSameValuesInsideItsSettledSignal() {
        present("p");
        set("p", T, values("s#0", 1));
        join("p");
        assertTrue(readInSettled.isAvailable());
        assertEquals(values("s#0", 1), readInSettled.values());
    }

    @Test
    void aFailedUpdateFailsEveryGroupAndTheErrorGoesOn() {
        side.groupTypes.add(B);
        present("p");
        join("p");
        assertThrows(IllegalStateException.class, () -> boundary.update("p", () -> {
            throw new IllegalStateException("test");
        }));
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        assertEquals(GroupAvailability.FAILED, availability("p", B));
        boundary.retry("p", () -> { });
        assertTrue(boundary.read("p", T).isAvailable());
        assertTrue(boundary.read("p", B).isAvailable());
    }

    @Test
    void aFailedUpdateLeavesATypeThatDoesNotApplyUnavailableAndSendsItNothing() {
        side.groupTypes.add(B);
        side.notApplying.add(B);
        present("zombie");
        join("zombie");
        assertThrows(IllegalStateException.class, () -> boundary.update("zombie", () -> {
            throw new IllegalStateException("test");
        }));
        assertEquals(GroupAvailability.FAILED, availability("zombie", T));
        assertEquals(GroupAvailability.UNAVAILABLE, availability("zombie", B));
        for (int i = 0; i < 3; i++) {
            boundary.retry("zombie", () -> { });
        }
        assertTrue(callsFor(B).isEmpty(), "a type that never applied gets no signal: " + calls);
        assertTrue(boundary.read("zombie", T).isAvailable());
    }

    @Test
    void aJoinWhoseObjectLeftDuringItsUpdateSendsNoPair() {
        present("p");
        boundary.join("p", () -> boundary.leave("p"));
        assertTrue(calls.isEmpty());
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T));
    }

    @Test
    void anUpdateThatKeepsFailingInRetriesIsContainedAndEndsAfterSixty() {
        present("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        int retries = 0;
        while (!boundary.withFailures().isEmpty() && retries < 1000) {
            boundary.retry("p", () -> {
                throw new IllegalStateException("update fails");
            });
            retries++;
        }
        assertEquals(60, retries, "a failing update counts as a failed try and does not restart the series");
        assertEquals(1, warnings.size());
        assertEquals(GroupAvailability.FAILED, availability("p", T));
    }

    @Test
    void aRetryWhoseUpdateConfirmsThenFailsStillEndsAfterSixty() {
        present("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        int retries = 0;
        while (!boundary.withFailures().isEmpty() && retries < 1000) {
            boundary.retry("p", () -> boundary.update("p", () -> boundary.commit("p", Collections.singleton(T),
                    () -> {
                        throw new IllegalStateException("commit fails after a confirmed invalidation");
                    })));
            retries++;
        }
        assertEquals(60, retries, "each retry counts once; the series does not restart");
        assertEquals(1, warnings.size());
        assertEquals(GroupAvailability.FAILED, availability("p", T));
    }

    @Test
    void aRetryWhoseObjectLeftDuringItsUpdateSendsNothingMore() {
        present("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        calls.clear();
        boundary.retry("p", () -> boundary.leave("p"));
        assertTrue(calls.isEmpty(), calls.toString());
        assertTrue(boundary.withFailures().isEmpty());
    }

    @Test
    void aReadWhoseAppliesToQuestionFailsAnswersFailed() {
        present("p");
        join("p");
        side.appliesThrows = true;
        assertEquals(GroupAvailability.FAILED, availability("p", T));
    }

    @Test
    void twentyFailedInvalidationsInARowDoNotSwitchTheSignalOff() {
        present("p");
        join("p");
        for (int i = 0; i < 20; i++) {
            side.answers(T).add("throw");
        }
        for (int i = 0; i < 20; i++) {
            change("p", T, values("s#0", i));
        }
        calls.clear();
        change("p", T, values("s#0", 99));
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {s#0=99.0}"), calls);
    }

    @Test
    void aReplacedObjectIsUnavailableForGoodAndALeftObjectRejoins() {
        present("old");
        set("old", T, values("s#0", 1));
        join("old");
        calls.clear();
        boundary.retire("old");
        assertEquals(Collections.singletonList("invalidate test:grouped old"), calls);
        assertEquals(GroupAvailability.UNAVAILABLE, availability("old", T));
        join("old");
        assertEquals(GroupAvailability.UNAVAILABLE, availability("old", T));

        present("p");
        join("p");
        boundary.leave("p");
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T));
        calls.clear();
        join("p");
        assertEquals(Arrays.asList("invalidate test:grouped p", "settled test:grouped p {}"), calls);
        assertTrue(boundary.read("p", T).isAvailable());
    }

    @Test
    void aReplacedObjectThatLeftFirstStillGetsItsInvalidation() {
        // Forge's order at a respawn: the dead player is removed (twice reported), then the clone event replaces it.
        present("old");
        set("old", T, values("s#0", 1));
        join("old");
        calls.clear();
        boundary.leave("old");
        boundary.leave("old");
        assertTrue(calls.isEmpty(), "leaving sends nothing");
        boundary.retire("old");
        assertEquals(Collections.singletonList("invalidate test:grouped old"), calls);
        assertEquals(GroupAvailability.UNAVAILABLE, availability("old", T));
    }

    @Test
    void aRejoinedObjectOwesNoInvalidationFromAnEarlierLeave() {
        present("p");
        join("p");
        boundary.leave("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        calls.clear();
        boundary.retire("p");
        assertTrue(calls.isEmpty(), "a failed group at retirement owes nothing, and the rejoin cleared the leave");
    }

    @Test
    void inForgeOrderAFailedGroupAtRespawnOwesNoInvalidation() {
        // join, a dimension change (leave, join), a failed change, then the respawn order: leave, leave, retire.
        present("p");
        join("p");
        boundary.leave("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        boundary.leave("p");
        boundary.leave("p");
        calls.clear();
        boundary.retire("p");
        assertTrue(calls.isEmpty(), "only groups available at the last leave are owed: " + calls);
    }

    @Test
    void leavingRemovesAnEntityFromTheRetriesEvenWhenItIsNoLongerJoined() {
        present("p");
        join("p");
        side.answers(T).add("no");
        change("p", T, values("s#0", 1));
        side.states.get("p").joined = false;
        boundary.leave("p");
        assertTrue(boundary.withFailures().isEmpty());
    }

    @Test
    void aTypeThatStopsApplyingGetsAnInvalidationAndIsUnavailable() {
        present("p");
        set("p", T, values("s#0", 1));
        join("p");
        side.notApplying.add(T);
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T), "the read checks it at once");
        calls.clear();
        change("p", T, values());
        assertEquals(Collections.singletonList("invalidate test:grouped p"), calls);
        assertEquals(GroupAvailability.UNAVAILABLE, availability("p", T));
    }

    @Test
    void aFailingAppliesToQuestionFailsTheGroupWithoutAnInvalidation() {
        present("p");
        join("p");
        side.appliesThrows = true;
        calls.clear();
        change("p", T, values("s#0", 1));
        assertTrue(calls.isEmpty());
        side.appliesThrows = false;
        assertEquals(GroupAvailability.FAILED, availability("p", T));
    }

    @Test
    void aFailedJoinLeavesTheGroupsFailedSoRetriesRecoverThem() {
        present("p");
        assertThrows(IllegalStateException.class, () -> boundary.join("p", () -> {
            throw new IllegalStateException("test");
        }));
        assertEquals(GroupAvailability.FAILED, availability("p", T));
        boundary.retry("p", () -> { });
        assertTrue(boundary.read("p", T).isAvailable());
    }

    @Test
    void theDiagnosticLogNamesEachSignalWithTheValuesOnlyWhenOn() {
        present("p");
        set("p", T, values("s#0", 1));
        join("p");
        assertTrue(diagnostics.stream().anyMatch(l -> l.contains("invalidate confirmed")), diagnostics.toString());
        assertTrue(diagnostics.stream().anyMatch(l -> l.contains("UPDATING -> AVAILABLE")), diagnostics.toString());
        assertTrue(diagnostics.stream().allMatch(l -> l.startsWith("group type=" + T + " entity=p ")
                && l.endsWith("values={s#0=1.0}")), diagnostics.toString());
        diagnosticOn = false;
        diagnostics.clear();
        change("p", T, values("s#0", 2));
        assertTrue(diagnostics.isEmpty());
    }
}
