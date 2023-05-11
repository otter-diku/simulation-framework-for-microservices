package org.invariantgenerator.invariantlanguage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Datatype to model a pattern which will be translated
 * into a flink CEP pattern.
 */
public class SequenceNode {
    public Boolean negated;

    public SequenceNodeQuantifier type;

    public List<String> eventIds;

    public int position;

    // c a b d a
    // c, (a | b)+ ONE_OR_MORE(greedy)
    // c a b d a -> c a b a

    // c a b d a
    // c, (a | b)
    // c a b d a -> c a, c b, c a

    // c a b d a
    // c, (a | b)* one_or_more.optional.greedy
    // c a b d a -> c, c a, c a b, c a b a, c b a, c b, c a


    // c a b d ->   c a d, c b d
    // a b (get("OR1")

    // pattern("OR1").where(simpcond(type = a || type = b))


    public SequenceNode(Boolean negated, SequenceNodeQuantifier type, List<String> eventIds, int position) {
        this.negated = negated;
        this.type = type;
        this.eventIds = eventIds;
        this.position = position;
    }

    public boolean compareWith(SequenceNode other) {
        return this.negated == other.negated
                && this.type == other.type
                && this.eventIds.stream().sorted().collect(Collectors.toList()).equals(other.eventIds.stream().sorted().collect(Collectors.toList()));
    }

    @Override
    public String toString() {
        return "Pattern{" +
                "Position=" + position +
                ", Negated=" + negated +
                ", Type=" + type +
                ", EventIds='" + eventIds + '\'' +
                '}';
    }

    public String getName() {
        return eventIds.toString();
    }

    public static class SequenceNodeBuilder {
        private Boolean isNeg;
        private SequenceNodeQuantifier type;
        private final List<String> eventIds;
        private int position;


        public SequenceNodeBuilder(List<String> eventIds) {
            this.eventIds = eventIds;
        }

        public void setNeg(Boolean neg) {
            isNeg = neg;
        }

        public void setType(SequenceNodeQuantifier type) {
            this.type = type;
        }
        public void setPosition(int position) {
            this.position = position;
        }

        public SequenceNode build() {
            return new SequenceNode(isNeg, type, eventIds, position);
        }

    }
}
