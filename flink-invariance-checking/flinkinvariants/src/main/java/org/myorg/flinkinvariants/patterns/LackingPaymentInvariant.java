package org.myorg.flinkinvariants.patterns;

import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.myorg.flinkinvariants.events.OrderPaymentFailedIntegrationEvent;
import org.myorg.flinkinvariants.events.OrderPaymentSucceededIntegrationEvent;
import org.myorg.flinkinvariants.events.OrderStatusChangedToSubmittedIntegrationEvent;

public class LackingPaymentInvariant {
    public static Pattern<EshopRecord, ?> InvariantPattern = Pattern.<EshopRecord>begin("orderSubmitted")
            .where(new SimpleCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord eshopRecord) throws Exception {
                    return eshopRecord.EventName.equals(OrderStatusChangedToSubmittedIntegrationEvent.EventName);
                }
            })
            .followedBy("paymentOutcome")
            .where(new IterativeCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord paymentEvent, Context<EshopRecord> context) throws Exception {
                    if (!(paymentEvent.EventName.equals(OrderPaymentSucceededIntegrationEvent.EventName) || paymentEvent.EventName.equals(OrderPaymentFailedIntegrationEvent.EventName))) {
                        return false;
                    }

                    for (var orderSubmittedEvent : context.getEventsForPattern("orderSubmitted")) {

                        var affectedProductId = orderSubmittedEvent.EventBody.get("OrderId").asInt();
                        return affectedProductId == paymentEvent.EventBody.get("OrderId").asInt();
                    }

                    return false;
                }
            })
            .within(Time.seconds(5));

    public static Pattern<EshopRecord, ?> InvariantPatternNotFollowedBy = Pattern.<EshopRecord>begin("orderSubmitted")
            .where(new SimpleCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord eshopRecord) throws Exception {
                    return eshopRecord.EventName.equals(OrderStatusChangedToSubmittedIntegrationEvent.EventName);
                }
            })
            .notFollowedBy("paymentOutcome")
            .where(new IterativeCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord paymentEvent, Context<EshopRecord> context) throws Exception {
                    if (!(paymentEvent.EventName.equals(OrderPaymentSucceededIntegrationEvent.EventName) || paymentEvent.EventName.equals(OrderPaymentFailedIntegrationEvent.EventName))) {
                        return false;
                    }

                    for (var orderSubmittedEvent : context.getEventsForPattern("orderSubmitted")) {

                        var affectedProductId = orderSubmittedEvent.EventBody.get("OrderId").asInt();
                        return affectedProductId == paymentEvent.EventBody.get("OrderId").asInt();
                    }

                    return false;
                }
            })
            .within(Time.seconds(5));
}
