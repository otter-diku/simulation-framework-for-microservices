package org.myorg.flinkinvariants.events;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;

public class EShopIntegrationEvent {

    public String EventName;

    public JsonNode EventBody;

    private Long eventTime;

    public EShopIntegrationEvent() {

    }

    public EShopIntegrationEvent(String EventName, JsonNode EventBody, Long eventTime) {
        this.EventName = EventName;
        this.EventBody = EventBody;
        this.eventTime = eventTime;
    }

    @Override
    public String toString() {
        return "EventName: " + EventName + ", " + "EventBody: " + EventBody + ", eventTime: " + eventTime;
    }

    public Long getTimestamp() {
        return eventTime;
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
                Objects.equals(EventBody, r.EventBody) &&
                Objects.equals(eventTime, r.eventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EventName, EventBody, eventTime);
    }
}
