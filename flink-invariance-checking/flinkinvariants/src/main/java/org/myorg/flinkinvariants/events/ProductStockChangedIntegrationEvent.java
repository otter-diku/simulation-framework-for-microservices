package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;
public class ProductStockChangedIntegrationEvent extends EshopRecord {

    public static String EventName = "ProductStockChangedIntegrationEvent";
    public ProductStockChangedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
