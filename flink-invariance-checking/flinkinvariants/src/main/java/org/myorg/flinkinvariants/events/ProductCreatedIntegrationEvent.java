package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class ProductCreatedIntegrationEvent extends EshopRecord {
    public static String EventName = "ProductCreatedIntegrationEvent";
    public ProductCreatedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
