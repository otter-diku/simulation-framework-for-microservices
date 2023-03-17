package org.myorg.flinkinvariants.events;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EshopRecord {

    public String EventName;

    public JsonNode EventBody;


    public EshopRecord(String EventName, JsonNode EventBody) {
        this.EventName = EventName;
        this.EventBody = EventBody;
    }

    @Override
    public String toString() {
        return "EventName: " + EventName + ", " + "EventBody: " + EventBody;
    }
}
