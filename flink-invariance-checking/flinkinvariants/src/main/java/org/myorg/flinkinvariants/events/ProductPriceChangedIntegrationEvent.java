package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class ProductPriceChangedIntegrationEvent extends EshopRecord {

    public static String EventName = "ProductPriceChangedIntegrationEvent";
    public ProductPriceChangedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}

