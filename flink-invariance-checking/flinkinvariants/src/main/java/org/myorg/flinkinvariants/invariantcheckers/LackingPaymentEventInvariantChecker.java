package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LackingPaymentEventInvariantChecker {
    final static OutputTag<String> outputTag = new OutputTag<>("lacking-payments") {
    };

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


        // var dataStreamSource = KafkaReader.GetDataStreamSourceEventTime(env, Duration.ofSeconds(1));
        // TODO: problems that streams from files are bounded
        var streamSource = FileReader
                .GetDataStreamSource(env, "/src/lacking_payment_1.json")
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                                forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));

//        var streamSource = dataStreamSource.filter((FilterFunction<EShopIntegrationEvent>) record
//                -> !record.EventName.equals(EventType.OrderPaymentSucceededIntegrationEvent.name())
//                && !record.EventName.equals(EventType.OrderPaymentFailedIntegrationEvent.name()))
//                .setParallelism(1);

        // streamSource.print().setParallelism(1);

        var patternStream = CEP.pattern(streamSource, InvariantPattern);
        var matches = patternStream
                .inEventTime()
                .process(new MyPatternProcessFunction());

        matches.print().setParallelism(1);

        var partialMatches = matches.getSideOutput(outputTag);
        partialMatches.print().setParallelism(1);

        System.out.println("Started CEP query for Price Changed Invariant..");
        env.execute("Flink Eshop Product Price Changed Invariant");
    }

    public static class MyPatternProcessFunction extends PatternProcessFunction<EShopIntegrationEvent, String> implements TimedOutPartialMatchHandler<EShopIntegrationEvent> {

        @Override
        public void processMatch(Map map, Context context, Collector collector) {
            collector.collect("Correct Behaviour: " + map.toString());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<EShopIntegrationEvent>> map, Context context) {
            // timed out match means we are missing a payment event
            var orderSubmittedEvent = map.get("orderSubmitted").get(0);
            context.output(outputTag, "Violation missing payment event for: " + orderSubmittedEvent.toString());
        }
    }

    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern = Pattern.<EShopIntegrationEvent>begin("orderSubmitted")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
                    return eshopIntegrationEvent.EventName.equals(EventType.OrderStatusChangedToSubmittedIntegrationEvent.name());
                }
            })
            .followedBy("paymentOutcome")
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent paymentEvent, Context<EShopIntegrationEvent> context) throws Exception {
                    if (!(paymentEvent.EventName.equals(EventType.OrderPaymentSucceededIntegrationEvent.name())
                            || paymentEvent.EventName.equals(EventType.OrderPaymentFailedIntegrationEvent.name()))) {
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
