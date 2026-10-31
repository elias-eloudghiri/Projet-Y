package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AssignmentBalancerTest {

    /** Characters in story order, c(0) being the narrator. */
    private final List<UUID> characters = ids(8);

    private final List<UUID> players = ids(4);

    @Test
    void secondPlayerGetsTheFirstCharactersInStoryOrder() {
        Map<UUID, UUID> all = allTo(p(0), 4);

        Map<UUID, UUID> result = join(all, 2);

        assertThat(result)
                .containsEntry(c(0), p(1))
                .containsEntry(c(1), p(1))
                .containsEntry(c(2), p(0))
                .containsEntry(c(3), p(0));
    }

    @Test
    void oddCountLeavesTheExtraCharacterToTheEarlierPlayer() {
        Map<UUID, UUID> result = join(allTo(p(0), 5), 2);

        assertThat(counts(result, 2)).containsExactly(3, 2);
    }

    @Test
    void tieBetweenDonorsGoesToTheEarliestArrival() {
        // After the second join: p0 has c3..c5, p1 has c0..c2.
        Map<UUID, UUID> twoPlayers = join(allTo(p(0), 6), 2);

        Map<UUID, UUID> result = join(twoPlayers, 3);

        assertThat(counts(result, 3)).containsExactly(2, 2, 2);
        assertThat(result).containsEntry(c(3), p(2)).containsEntry(c(0), p(2));
    }

    @ParameterizedTest(name = "{0} players, {1} characters")
    @CsvSource({"2, 2", "2, 3", "2, 4", "3, 3", "3, 4", "3, 6", "3, 7", "4, 4", "4, 5", "4, 8"})
    void eachArrivalKeepsTheGapAtMostOne(int playerCount, int characterCount) {
        Map<UUID, UUID> assignments = allTo(p(0), characterCount);
        for (int joined = 2; joined <= playerCount; joined++) {
            Map<UUID, UUID> before = assignments;
            assignments = join(assignments, joined);

            List<Integer> counts = counts(assignments, joined);
            assertThat(max(counts) - min(counts)).isLessThanOrEqualTo(1);
            assertThat(min(counts)).isPositive();
            // Every character that moved went to the newcomer.
            UUID newcomer = p(joined - 1);
            for (UUID character : before.keySet()) {
                if (!before.get(character).equals(assignments.get(character))) {
                    assertThat(assignments.get(character)).isEqualTo(newcomer);
                }
            }
        }
        assertThat(assignments).hasSize(characterCount);
    }

    @Test
    void arrivalAfterAManualChangeOnlyFeedsTheNewcomer() {
        // The creator took back characters from p1: p0 has 5, p1 has 1 (c5).
        Map<UUID, UUID> manual = allTo(p(0), 6);
        manual.put(c(5), p(1));

        Map<UUID, UUID> result = join(manual, 3);

        assertThat(counts(result, 3)).containsExactly(3, 1, 2);
        assertThat(result).containsEntry(c(5), p(1));
        assertThat(result).containsEntry(c(0), p(2)).containsEntry(c(1), p(2));
    }

    @Test
    void nothingMovesWhenTheNewcomerCannotGetCloser() {
        // p0 and p1 have one character each: taking one would leave a player with none.
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        assignments.put(c(0), p(0));
        assignments.put(c(1), p(1));

        Map<UUID, UUID> result =
                AssignmentBalancer.giveToNewcomer(
                        characters.subList(0, 2), assignments, players.subList(0, 3), p(2));

        assertThat(result).isEqualTo(assignments);
    }

    @Test
    void givenMapIsLeftUntouchedAndResultIsDeterministic() {
        Map<UUID, UUID> all = allTo(p(0), 7);
        Map<UUID, UUID> copy = new HashMap<>(all);

        Map<UUID, UUID> first = join(all, 2);
        Map<UUID, UUID> second = join(all, 2);

        assertThat(all).isEqualTo(copy);
        assertThat(first).isEqualTo(second);
    }

    // --- Helpers ---

    /** The player at {@code joined - 1} arrives; the ones before are already in. */
    private Map<UUID, UUID> join(Map<UUID, UUID> assignments, int joined) {
        return AssignmentBalancer.giveToNewcomer(
                characters.subList(0, assignments.size()),
                assignments,
                players.subList(0, joined),
                p(joined - 1));
    }

    private Map<UUID, UUID> allTo(UUID player, int characterCount) {
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        characters.subList(0, characterCount).forEach(c -> assignments.put(c, player));
        return assignments;
    }

    /** Number of characters of each of the first players, in arrival order. */
    private List<Integer> counts(Map<UUID, UUID> assignments, int playerCount) {
        List<Integer> counts = new ArrayList<>();
        for (UUID player : players.subList(0, playerCount)) {
            counts.add((int) assignments.values().stream().filter(player::equals).count());
        }
        return counts;
    }

    private UUID c(int index) {
        return characters.get(index);
    }

    private UUID p(int index) {
        return players.get(index);
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private static int max(Collection<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }

    private static int min(Collection<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).min().orElseThrow();
    }
}
