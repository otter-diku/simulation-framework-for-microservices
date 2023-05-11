package org.invariantchecker.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class Event {

    public String Type;

    public JsonNode Content;

    public Event() {

    }
    public Event(String type, JsonNode content) {
        Type = type;
        Content = content;
    }

    public Event(String type, String content) {
        Type = type;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Content = objectMapper.readTree(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "Event{" + "Type='" + Type + '\'' + ", Content=" + Content + '}';
    }
}
