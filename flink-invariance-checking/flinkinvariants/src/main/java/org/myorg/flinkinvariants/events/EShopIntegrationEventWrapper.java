package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class EShopIntegrationEventWrapper {

    public final String Type;

    public final JsonNode Content;

    public EShopIntegrationEventWrapper(String type, JsonNode content) {
        this.Type = type;
        this.Content = content;
    }
}
