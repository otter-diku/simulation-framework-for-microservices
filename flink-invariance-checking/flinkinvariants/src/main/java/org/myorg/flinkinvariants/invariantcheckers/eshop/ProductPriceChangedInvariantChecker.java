package org.myorg.flinkinvariants.invariantcheckers.eshop;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;
import org.myorg.flinkinvariants.events.InvariantViolationEvent;
import org.myorg.flinkinvariants.sinks.SeqSink;

import java.time.Duration;
import java.util.*;

public class ProductPriceChangedInvariantChecker  {

    private static final int MAX_LATENESS_OF_EVENT = 5;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);

        var streamSource =
                KafkaReader.GetDataStreamSource(env)
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        CheckProductPriceChangedInvariant(env, streamSource, new SeqSink());
    }

    public static void CheckProductPriceChangedInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<InvariantViolationEvent> sinkFunction)
            throws Exception {

        var filteredStream =
                input.filter(
                                e ->
                                        e.getEventName()
                                                        .equals(
                                                                "UserCheckoutAcceptedIntegrationEvent")
                                                || e.getEventName()
                                                        .equals(
                                                                "ProductPriceChangedIntegrationEvent"))
                        .setParallelism(1);

        var patternStream = CEP.pattern(filteredStream, InvariantPattern);
        var matches =
                patternStream
                        .inEventTime()
                        .process(
                                new PatternProcessFunction<EShopIntegrationEvent, InvariantViolationEvent>() {
                                    @Override
                                    public void processMatch(
                                            Map<String, List<EShopIntegrationEvent>> map,
                                            Context context,
                                            Collector<InvariantViolationEvent> collector) {
                                        collector.collect(getInvariantViolationEvent(map));
                                    }
                                })
                        .addSink(sinkFunction);

        env.execute("Flink Eshop Product Price Changed Invariant");
    }

    /***
     More verbose log message
    ***/
/*    private static InvariantViolationEvent getInvariantViolationEvent(Map<String, List<EShopIntegrationEvent>> map) {

        var firstPriceChange = map.get("firstPriceChange")
                .iterator()
                .next();

        var userCheckoutWithOutDatedItemPrice = map.get("userCheckoutWithOutDatedItemPrice")
                .iterator()
                .next();

        var violationEvent = new InvariantViolationEvent();
        violationEvent.setInvariantName("PriceChange");
        violationEvent.setTimestamp(userCheckoutWithOutDatedItemPrice.getEventTimeAsString());
        violationEvent.setMessageTemplate("Price changed at: {PriceChangedAt} with new price {NewPrice} for product ID: {ProductId}. Basket checked out at {BasketCheckedOutAt} with price of {StalePrice}");

        violationEvent.getArguments().put("PriceChangedAt", firstPriceChange.getEventTimeAsString());
        violationEvent.getArguments().put("NewPrice", firstPriceChange.getEventBody().get("NewPrice"));
        var productId = firstPriceChange.getEventBody().get("ProductId");
        violationEvent.getArguments().put("ProductId", productId);
        violationEvent.getArguments().put("BasketCheckedOutAt", userCheckoutWithOutDatedItemPrice.getEventTimeAsString());

        var items = userCheckoutWithOutDatedItemPrice
                .getEventBody()
                .get("Basket")
                .get("Items")
                .elements();

        while (items.hasNext()) {
            var next = items.next();
            if (next.get("ProductId").equals(productId)) {
                violationEvent.getArguments().put("StalePrice", next.get("UnitPrice"));
                break;
            }
        }
        return violationEvent;
    }*/

    private static InvariantViolationEvent getInvariantViolationEvent(Map<String, List<EShopIntegrationEvent>> map) {
        var userCheckoutWithOutDatedItemPrice = map.get("userCheckoutWithOutDatedItemPrice")
                .iterator()
                .next();

        return new InvariantViolationEvent(userCheckoutWithOutDatedItemPrice.getEventTimeAsString(),
                "{InvariantName} invariant violated",
                Collections.singletonMap("InvariantName", "ProductPriceChangedInvariant"));
    }

    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern =
            Pattern.<EShopIntegrationEvent>begin("firstPriceChange")
                    .where(
                            new SimpleCondition<>() {
                                @Override
                                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
                                    return eshopIntegrationEvent
                                            .getEventName()
                                            .equals(
                                                    EventType.ProductPriceChangedIntegrationEvent
                                                            .name());
                                }
                            })
                    .notFollowedBy("subsequentPriceChange")
                    .where(
                            new IterativeCondition<>() {
                                @Override
                                public boolean filter(
                                        EShopIntegrationEvent subsequentPriceChangeEvent,
                                        Context<EShopIntegrationEvent> context)
                                        throws Exception {
                                    if (!subsequentPriceChangeEvent
                                            .getEventName()
                                            .equals(
                                                    EventType.ProductPriceChangedIntegrationEvent
                                                            .name())) return false;

                                    for (var firstPriceChangeEvent :
                                            context.getEventsForPattern("firstPriceChange")) {
                                        return firstPriceChangeEvent
                                                        .getEventBody()
                                                        .get("ProductId")
                                                        .asInt()
                                                == subsequentPriceChangeEvent
                                                        .getEventBody()
                                                        .get("ProductId")
                                                        .asInt();
                                    }

                                    return false;
                                }
                            })
                    .followedByAny("userCheckoutWithOutDatedItemPrice")
                    .where(
                            new IterativeCondition<>() {
                                @Override
                                public boolean filter(
                                        EShopIntegrationEvent userCheckoutEvent,
                                        Context<EShopIntegrationEvent> context)
                                        throws Exception {
                                    if (!userCheckoutEvent
                                            .getEventName()
                                            .equals("UserCheckoutAcceptedIntegrationEvent")) {
                                        return false;
                                    }

                                    for (var priceChangeEvent :
                                            context.getEventsForPattern("firstPriceChange")) {

                                        var affectedProductId =
                                                priceChangeEvent
                                                        .getEventBody()
                                                        .get("ProductId")
                                                        .asInt();
                                        var newPrice =
                                                priceChangeEvent
                                                        .getEventBody()
                                                        .get("NewPrice")
                                                        .asDouble();

                                        for (Iterator<JsonNode> it =
                                                        userCheckoutEvent
                                                                .getEventBody()
                                                                .get("Basket")
                                                                .get("Items")
                                                                .elements();
                                                it.hasNext(); ) {
                                            var item = it.next();
                                            if (affectedProductId == item.get("ProductId").asInt()
                                                    && newPrice != item.get("UnitPrice").asDouble())
                                                return true;
                                        }
                                    }

                                    return false;
                                }
                            })
                    .within(Time.seconds(30));
}
