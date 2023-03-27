package org.myorg.flinkinvariants.events;


import com.fasterxml.jackson.databind.JsonNode;

public class OrderPaymentFailedIntegrationEvent extends EshopRecord {

    public static String EventName = "OrderPaymentFailedIntegrationEvent";
    public OrderPaymentFailedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
