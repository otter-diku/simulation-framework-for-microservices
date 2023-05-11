package org.invariantchecker.events;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class InvariantViolationEvent {
    private String Timestamp;
    private String MessageTemplate;

    private Map<String, Object> Arguments;

    public InvariantViolationEvent(String timestamp, String messageTemplate, Map<String, Object> arguments) {
        Timestamp = timestamp;
        MessageTemplate = messageTemplate;
        Arguments = arguments;
    }

    public Map<String, Object> getArguments() {
        return Arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        Arguments = arguments;
    }

    public String getTimestamp() {
        return Timestamp;
    }

    public void setTimestamp(String timestamp) {
        Timestamp = timestamp;
    }

    public String getMessageTemplate() {
        return MessageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        MessageTemplate = messageTemplate;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvariantViolationEvent that = (InvariantViolationEvent) o;
        return Objects.equals(Timestamp, that.Timestamp) && Objects.equals(MessageTemplate, that.MessageTemplate) && Objects.equals(Arguments, that.Arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Timestamp, MessageTemplate, Arguments);
    }

    @Override
    public String toString() {
        var msg = "{ " +
                "\"@t\": \"" + getTimestamp() + "\"" +
                ", \"@mt\": \"" + getMessageTemplate() + "\""+
                ", \"@l\": \"Information\"";

        if (!getArguments().isEmpty()) {
            var args = getArguments()
                    .entrySet()
                    .stream()
                    .map(kv -> "\"" + kv.getKey() + "\": " + "\"" + (kv.getValue() == null ? "" : kv.getValue()) + "\"")
                    .collect(Collectors.joining(","));
            msg = msg + ", " + args;
        }

        return  msg + " }";
    }
}