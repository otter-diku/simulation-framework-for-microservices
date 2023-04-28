package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.kafka.common.protocol.types.Field;

/**
 * Datatype to model a pattern which will be translated
 * into a flink CEP pattern.
 */
public class Pattern {
    public Boolean Negated;

    public PatternType Type;

    public Contiguity Contiguity;

    public String EventType;

    public String EventId;

    public Pattern(Boolean negated, PatternType type, Contiguity contiguity, String eventType, String eventId) {
        Negated = negated;
        Type = type;
        Contiguity = contiguity;
        EventType = eventType;
        EventId = eventId;
    }

    @Override
    public String toString() {
        return "Pattern{" +
                "Negated=" + Negated +
                ", Type=" + Type +
                ", Contiguity=" + Contiguity +
                ", EventType='" + EventType + '\'' +
                ", EventId='" + EventId + '\'' +
                '}';
    }

    public static class PatternBuilder {
        private Boolean isNeg;
        private PatternType type;
        private String eventType;
        private String eventId;

        private Contiguity contiguity;

        public PatternBuilder(String eventType, String eventId) {
            this.eventType = eventType;
            this.eventId = eventId;
        }

        public void setNeg(Boolean neg) {
            isNeg = neg;
        }

        public void setType(PatternType type) {
            this.type = type;
        }

        public void setContiguity(org.myorg.flinkinvariants.invariantlanguage.Contiguity contiguity) {
            this.contiguity = contiguity;
        }

        public Pattern build() {
            return new Pattern(isNeg, type, contiguity, eventType, eventId);
        }

    }
}
