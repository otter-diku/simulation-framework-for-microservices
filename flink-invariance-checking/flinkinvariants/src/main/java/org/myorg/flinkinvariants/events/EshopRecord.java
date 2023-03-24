package org.myorg.flinkinvariants.events;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

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


    /*
     * The events in the DataStream to which you want to apply pattern matching must implement proper equals() and hashCode()
     * methods because FlinkCEP uses them for comparing and matching events.
     */
    @Override
    public boolean equals(Object o) {

        if (o == this) return true;
        if (!(o instanceof EshopRecord)) {
            return false;
        }
        EshopRecord r = (EshopRecord) o;
        return  Objects.equals(EventName, r.EventName) &&
                Objects.equals(EventBody, r.EventBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EventName, EventBody);
    }
}
