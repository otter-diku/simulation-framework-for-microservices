package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class ProductDeletedIntegrationEvent extends EshopRecord {
    public static String EventName = "ProductDeletedIntegrationEvent";
    public ProductDeletedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
