package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class OrderPaymentSucceededIntegrationEvent extends EshopRecord {

    public static String EventName = "OrderPaymentSucceededIntegrationEvent";
    public OrderPaymentSucceededIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
