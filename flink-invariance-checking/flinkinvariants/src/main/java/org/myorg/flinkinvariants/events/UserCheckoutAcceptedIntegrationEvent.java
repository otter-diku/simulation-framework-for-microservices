package org.myorg.flinkinvariants.events;

import com.fasterxml.jackson.databind.JsonNode;

public class UserCheckoutAcceptedIntegrationEvent extends EshopRecord {

    public static String EventName = "UserCheckoutAcceptedIntegrationEvent";

    public UserCheckoutAcceptedIntegrationEvent(JsonNode EventBody) {
        super(EventName, EventBody);
    }
}
