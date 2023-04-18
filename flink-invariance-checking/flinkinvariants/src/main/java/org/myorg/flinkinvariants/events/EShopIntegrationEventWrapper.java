package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class EShopIntegrationEventWrapper {

    public String Type;

    public JsonNode Content;

    public EShopIntegrationEventWrapper(String type, JsonNode content) {
        this.Type = type;
        this.Content = content;
    }

    // Dummy constructor for json serialization
    public EShopIntegrationEventWrapper() {}
}
