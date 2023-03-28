package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderStatusChangedToSubmittedIntegrationEvent extends EshopRecord {
    public static String EventName = "OrderStatusChangedToSubmittedIntegrationEvent";
    public OrderStatusChangedToSubmittedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
