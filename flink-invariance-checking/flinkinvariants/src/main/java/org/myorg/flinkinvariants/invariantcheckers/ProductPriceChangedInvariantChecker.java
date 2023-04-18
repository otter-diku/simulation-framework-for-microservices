package org.myorg.flinkinvariants.invariantcheckers;

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

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ProductPriceChangedInvariantChecker {

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

        CheckProductPriceChangedInvariant(env, streamSource, new PrintSinkFunction<>());
    }

    public static void CheckProductPriceChangedInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<String> sinkFunction)
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
                                new PatternProcessFunction<EShopIntegrationEvent, String>() {
                                    @Override
                                    public void processMatch(
                                            Map<String, List<EShopIntegrationEvent>> map,
                                            Context context,
                                            Collector<String> collector) {
                                        collector.collect(map.toString());
                                    }
                                })
                        .addSink(sinkFunction);

        env.execute("Flink Eshop Product Price Changed Invariant");
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
