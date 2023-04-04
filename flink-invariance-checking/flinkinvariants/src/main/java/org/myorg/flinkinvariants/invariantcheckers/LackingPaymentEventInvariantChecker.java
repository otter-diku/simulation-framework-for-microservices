package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
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

        var streamSource = KafkaReader.GetDataStreamSourceEventTime(env, Duration.ofSeconds(1));

//        var streamSource = dataStreamSource.filter((FilterFunction<EShopIntegrationEvent>) record
//                -> !record.EventName.equals(EventType.OrderPaymentSucceededIntegrationEvent.name())
//                && !record.EventName.equals(EventType.OrderPaymentFailedIntegrationEvent.name()))
//                .setParallelism(1);

        CheckLackingPaymentInvariant(env, streamSource, new PrintSinkFunction<>());
    }

    public static void CheckLackingPaymentInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<String> sinkFunction) throws Exception {

        var patternStream = CEP.pattern(input, InvariantPattern);
        var matches = patternStream
                .inEventTime()
                .process(new MyPatternProcessFunction());

        var partialMatches = matches.getSideOutput(outputTag);
        partialMatches.addSink(sinkFunction);

        env.execute("Flink Eshop Lacking Payment Invariant");
    }

    public static void CheckLackingPaymentInvariantNotFollowed(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<String> sinkFunction) throws Exception {

        var patternStream = CEP.pattern(input, InvariantPatternNotFollowed);
        var matches = patternStream
                .inEventTime()
                .process(new MyPatternProcessFunction())
                .addSink(sinkFunction);

        env.execute("Flink Eshop Lacking Payment Invariant");
    }


    public static class MyPatternProcessFunction extends PatternProcessFunction<EShopIntegrationEvent, String> implements TimedOutPartialMatchHandler<EShopIntegrationEvent> {

        @Override
        public void processMatch(Map map, Context context, Collector collector) {
            collector.collect(map.toString());
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

    public static Pattern<EShopIntegrationEvent, ?> InvariantPatternNotFollowed = Pattern.<EShopIntegrationEvent>begin("orderSubmitted")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
                    return eshopIntegrationEvent.EventName.equals(EventType.OrderStatusChangedToSubmittedIntegrationEvent.name());
                }
            })
            .notFollowedBy("paymentOutcome")
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
