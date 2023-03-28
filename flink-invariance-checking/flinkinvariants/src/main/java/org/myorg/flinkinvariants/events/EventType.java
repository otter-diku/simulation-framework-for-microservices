package org.myorg.flinkinvariants.events;

public enum EventType {
    OrderPaymentFailedIntegrationEvent,
    OrderPaymentSucceededIntegrationEvent,
    OrderStatusChangedToSubmittedIntegrationEvent,
    ProductBoughtIntegrationEvent,
    ProductCreatedIntegrationEvent,
    ProductDeletedIntegrationEvent,
    ProductPriceChangedIntegrationEvent,
    ProductStockChangedIntegrationEvent,
    UserCheckoutAcceptedIntegrationEvent
}
