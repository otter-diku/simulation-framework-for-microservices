package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class ProductBoughtIntegrationEvent extends EshopRecord {
    public static String EventName = "ProductBoughtIntegrationEvent";
    public ProductBoughtIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
