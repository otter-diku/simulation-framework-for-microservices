package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public class EShopIntegrationEvent {

    private String EventName;
    private JsonNode EventBody;
    private Long EventTime;

    public String getEventName() {
        return EventName;
    }

    public void setEventName(String eventName) {
        EventName = eventName;
    }

    public JsonNode getEventBody() {
        return EventBody;
    }

    public void setEventBody(JsonNode eventBody) {
        EventBody = eventBody;
    }

    public Long getEventTime() {
        return EventTime;
    }

    public void setEventTime(Long eventTime) {
        EventTime = eventTime;
    }

    public EShopIntegrationEvent() {}

    public EShopIntegrationEvent(String EventName, JsonNode EventBody, Long eventTime) {
        this.EventName = EventName;
        this.EventBody = EventBody;
        this.EventTime = eventTime;
    }

    @Override
    public String toString() {
        return "EventName: "
                + EventName
                + ", "
                + "EventBody: "
                + EventBody
                + ", eventTime: "
                + EventTime;
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
        return Objects.equals(EventName, r.EventName)
                && Objects.equals(EventBody, r.EventBody)
                && Objects.equals(EventTime, r.EventTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EventName, EventBody, EventTime);
    }
}
