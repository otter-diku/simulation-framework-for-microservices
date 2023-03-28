package org.myorg.flinkinvariants.events;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public class EShopIntegrationEvent {

    public final String EventName;

    public final JsonNode EventBody;

    public EShopIntegrationEvent(String EventName, JsonNode EventBody) {
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
        if (!(o instanceof EShopIntegrationEvent)) {
            return false;
        }
        EShopIntegrationEvent r = (EShopIntegrationEvent) o;
        return  Objects.equals(EventName, r.EventName) &&
                Objects.equals(EventBody, r.EventBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EventName, EventBody);
    }
}
