package com.example.back.room;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hands characters to a player who just joined. Only the newcomer receives characters: they are
 * taken one by one from the player who has the most, until that player has at most one more than
 * the newcomer. The other attributions do not move, so that changes made by the creator are kept.
 *
 * <p>Deterministic: ties between donors go to the earliest arrival, and a donor gives its first
 * character in story order.
 */
final class AssignmentBalancer {

    private AssignmentBalancer() {}

    /**
     * @param characters character ids in story order
     * @param assignments player of each character
     * @param players player ids in arrival order, the newcomer included
     * @return the new assignments (the given map is left untouched)
     */
    static Map<UUID, UUID> giveToNewcomer(
            List<UUID> characters, Map<UUID, UUID> assignments, List<UUID> players, UUID newcomer) {
        Map<UUID, UUID> result = new LinkedHashMap<>(assignments);
        Map<UUID, Integer> counts = new HashMap<>();
        players.forEach(p -> counts.put(p, 0));
        result.values().forEach(p -> counts.computeIfPresent(p, (k, n) -> n + 1));

        Comparator<UUID> mostFirst =
                Comparator.<UUID>comparingInt(counts::get)
                        .reversed()
                        .thenComparingInt(players::indexOf);
        while (true) {
            UUID donor =
                    players.stream().filter(p -> !p.equals(newcomer)).min(mostFirst).orElse(null);
            if (donor == null || counts.get(donor) - counts.get(newcomer) <= 1) {
                return result;
            }
            UUID given =
                    characters.stream()
                            .filter(c -> donor.equals(result.get(c)))
                            .findFirst()
                            .orElseThrow();
            result.put(given, newcomer);
            counts.merge(donor, -1, Integer::sum);
            counts.merge(newcomer, 1, Integer::sum);
        }
    }
}
