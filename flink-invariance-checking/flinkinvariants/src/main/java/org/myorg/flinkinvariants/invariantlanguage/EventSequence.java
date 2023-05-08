package org.myorg.flinkinvariants.invariantlanguage;

import java.util.*;
import java.util.stream.Collectors;

public class EventSequence {
    /*
    *   We keep this map to keep track of the position of each event in the sequence.
    *   Example:
    *   `SEQ (a, (b|c), !d, e)`
    *   eventIds = [
    *       [a -> 0],
    *       [b -> 1],
    *       [c -> 1].
    *       [d -> 2],
    *       [e -> 3]
    *   ]
     */
    private Map<String, Integer> eventIds = new HashMap<>();

    private final List<SequenceNode> sequence = new ArrayList<>();

    public boolean addNode(SequenceNode node) {
        // TODO: This constraint seems to make sense at the moment, given the restrictive syntax for `SEQ (...)`
        // TODO: Double check this is correct though.

        var duplicateIds = node.eventIds
                .stream()
                .filter(eId -> eventIds.containsKey(eId))
                .collect(Collectors.toUnmodifiableList());

        if ((long) duplicateIds.size() > 0) {
            return false;
        }

        eventIds.putAll(node.eventIds
                .stream()
                .collect(Collectors.toMap(e -> e, e -> sequence.size())));

        sequence.add(node);
        return true;
    }

    public List<SequenceNode> getSequence() { return sequence; }

    public Integer getEventPositionById(String eventId) {
        return eventIds.getOrDefault(eventId, -1);
    }
}
