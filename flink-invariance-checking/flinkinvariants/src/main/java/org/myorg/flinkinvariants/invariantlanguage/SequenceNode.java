package org.myorg.flinkinvariants.invariantlanguage;

import java.util.List;

/**
 * Datatype to model a pattern which will be translated
 * into a flink CEP pattern.
 */
public class SequenceNode {
    public Boolean Negated;

    public SequenceNodeQuantifier Type;

    public List<String> EventIds;

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


    public SequenceNode(Boolean negated, SequenceNodeQuantifier type, List<String> eventIds) {
        Negated = negated;
        Type = type;
        EventIds = eventIds;
    }

    @Override
    public String toString() {
        return "Pattern{" +
                "Negated=" + Negated +
                ", Type=" + Type +
                ", EventIds='" + EventIds + '\'' +
                '}';
    }

    public String getName() {
        return EventIds.toString();
    }

    public static class SequenceNodeBuilder {
        private Boolean isNeg;
        private SequenceNodeQuantifier type;
        private final List<String> eventIds;


        public SequenceNodeBuilder(List<String> eventIds) {
            this.eventIds = eventIds;
        }

        public void setNeg(Boolean neg) {
            isNeg = neg;
        }

        public void setType(SequenceNodeQuantifier type) {
            this.type = type;
        }

        public SequenceNode build() {
            return new SequenceNode(isNeg, type, eventIds);
        }

    }
}
