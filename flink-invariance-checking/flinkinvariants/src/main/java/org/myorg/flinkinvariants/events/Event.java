package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class Event {

    public String Type;

    public JsonNode Content;

    public Event() {

    }

    public Event(String type, JsonNode content) {
        Type = type;
        Content = content;
    }

    @Override
    public String toString() {
        return "Event{" + "Type='" + Type + '\'' + ", Content=" + Content + '}';
    }
}
