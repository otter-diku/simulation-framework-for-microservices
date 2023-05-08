//package org.myorg.flinkinvariants.invariantcheckers.eshop;
//
//import org.apache.flink.api.common.eventtime.WatermarkStrategy;
//import org.apache.flink.api.common.functions.FilterFunction;
//import org.apache.flink.cep.CEP;
//import org.apache.flink.cep.functions.PatternProcessFunction;
//import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
//import org.apache.flink.cep.pattern.Pattern;
//import org.apache.flink.cep.pattern.conditions.IterativeCondition;
//import org.apache.flink.cep.pattern.conditions.SimpleCondition;
//import org.apache.flink.streaming.api.datastream.DataStream;
//import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
//import org.apache.flink.streaming.api.functions.sink.SinkFunction;
//import org.apache.flink.streaming.api.windowing.time.Time;
//import org.apache.flink.util.Collector;
//import org.apache.flink.util.OutputTag;
//import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
//import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
//import org.myorg.flinkinvariants.events.EventType;
//import org.myorg.flinkinvariants.events.InvariantViolationEvent;
//import org.myorg.flinkinvariants.sinks.SeqSink;
//
//import java.time.Duration;
//import java.util.*;
//
//public class LackingPaymentEventInvariantChecker {
//    static final OutputTag<String> outputTag = new OutputTag<>("lacking-payments") {};
//
//    private static final int MAX_LATENESS_OF_EVENT = 1;
//
//    public static void main(String[] args) throws Exception {
//        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//
//        var streamSource =
//                KafkaReader.GetDataStreamSource(env)
//                        .assignTimestampsAndWatermarks(
//                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
//                                                Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
//                                        .withIdleness(Duration.ofSeconds(2))
//                                        .withTimestampAssigner(
//                                                (event, timestamp) -> event.getEventTime()));
//
//        // TODO: lacking payment does not occur usually in eshop (therefore to trigger the
//        //  violation one needs to filter out the payment events.
//        // var streamSource = dataStreamSource.filter((FilterFunction<EShopIntegrationEvent>) record
//        //        ->
//        // !record.EventName.equals(EventType.OrderPaymentSucceededIntegrationEvent.name())
//        //        && !record.EventName.equals(EventType.OrderPaymentFailedIntegrationEvent.name()))
//        //        .setParallelism(1);
//
//        CheckLackingPaymentInvariant(env, streamSource, new SeqSink());
//    }
//
//    public static void CheckLackingPaymentInvariant(
//            StreamExecutionEnvironment env,
//            DataStream<EShopIntegrationEvent> input,
//            SinkFunction<InvariantViolationEvent> sinkFunction)
//            throws Exception {
//
//        var patternStream = CEP.pattern(input, InvariantPattern);
//        var matches = patternStream.inEventTime().process(new MyPatternProcessFunction());
//
//
//        var distinctMatches =
//                matches.getSideOutput(outputTag)
//                        .filter(
//                                new FilterFunction<InvariantViolationEvent>() {
//                                    private final Set<InvariantViolationEvent> seen = new HashSet<>();
//
//                                    @Override
//                                    public boolean filter(InvariantViolationEvent value) throws Exception {
//                                        return seen.add(value);
//                                    }
//                                })
//                        .setParallelism(1);
//
//        distinctMatches.addSink(sinkFunction);
//
//        env.execute("Flink Eshop Lacking Payment Invariant");
//    }
//
//    public static class MyPatternProcessFunction
//            extends PatternProcessFunction<EShopIntegrationEvent, InvariantViolationEvent>
//            implements TimedOutPartialMatchHandler<EShopIntegrationEvent> {
//        @Override
//        public void processMatch(Map<String, List<EShopIntegrationEvent>> map, Context context, Collector<String> collector) {
//            collector.collect(map.toString());
//        }
//
//        @Override
//        public void processTimedOutMatch(
//                Map<String, List<EShopIntegrationEvent>> map, Context context) {
//            // timed out match means we are missing a payment event
//            var orderSubmittedEvent = map.get("orderSubmitted").get(0);
//            // context.output(
//                    outputTag,
//                    "Violation missing payment event for: " + orderSubmittedEvent.toString());
//            context.output(outputTag, new InvariantViolationEvent(orderSubmittedEvent.getEventTimeAsString(),
//                    "{InvariantName} invariant violated",
//                    Collections.singletonMap("InvariantName", "LackingPaymentEventInvariant")));
//        }
//    }
//
//    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern =
//            Pattern.<EShopIntegrationEvent>begin("orderSubmitted")
//                    .where(
//                            new SimpleCondition<>() {
//                                @Override
//                                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
//                                    return eshopIntegrationEvent
//                                            .getEventName()
//                                            .equals(
//                                                    EventType
//                                                            .OrderStatusChangedToSubmittedIntegrationEvent
//                                                            .name());
//                                }
//                            })
//                    .followedBy("paymentOutcome")
//                    .where(
//                            new IterativeCondition<>() {
//                                @Override
//                                public boolean filter(
//                                        EShopIntegrationEvent paymentEvent,
//                                        Context<EShopIntegrationEvent> context)
//                                        throws Exception {
//                                    if (!(paymentEvent
//                                                    .getEventName()
//                                                    .equals(
//                                                            EventType
//                                                                    .OrderPaymentSucceededIntegrationEvent
//                                                                    .name())
//                                            || paymentEvent
//                                                    .getEventName()
//                                                    .equals(
//                                                            EventType
//                                                                    .OrderPaymentFailedIntegrationEvent
//                                                                    .name()))) {
//                                        return false;
//                                    }
//
//                                    for (var orderSubmittedEvent :
//                                            context.getEventsForPattern("orderSubmitted")) {
//
//                                        var affectedProductId =
//                                                orderSubmittedEvent
//                                                        .getEventBody()
//                                                        .get("OrderId")
//                                                        .asInt();
//                                        return affectedProductId
//                                                == paymentEvent
//                                                        .getEventBody()
//                                                        .get("OrderId")
//                                                        .asInt();
//                                    }
//
//                                    return false;
//                                }
//                            })
//                    .within(Time.seconds(5));
//
////    public static Pattern<EShopIntegrationEvent, ?> InvariantPatternNotFollowed =
//            Pattern.<EShopIntegrationEvent>begin("orderSubmitted")
//        //            .where(
//                            new SimpleCondition<>() {
//                //                @Override
//                //                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
//                //                    return eshopIntegrationEvent
//                                            .getEventName()
//                                            .equals(
//                                                    EventType
//                                                            .OrderStatusChangedToSubmittedIntegrationEvent
//                                                            .name());
//                //                }
//                //            })
//        //            .notFollowedBy("paymentOutcome")
//        //            .where(
//                            new IterativeCondition<>() {
//                //                @Override
//                //                public boolean filter(
//                                        EShopIntegrationEvent paymentEvent,
//                                        Context<EShopIntegrationEvent> context)
//                                        throws Exception {
//                //                    if (!(paymentEvent
//                                                    .getEventName()
//                                                    .equals(
//                                                            EventType
//                                                                    .OrderPaymentSucceededIntegrationEvent
//                                                                    .name())
//                //                            || paymentEvent
//                                                    .getEventName()
//                                                    .equals(
//                                                            EventType
//                                                                    .OrderPaymentFailedIntegrationEvent
//                                                                    .name()))) {
//                //                        return false;
//                //                    }
////
//                //                    for (var orderSubmittedEvent :
//                                            context.getEventsForPattern("orderSubmitted")) {
////
//                //                        var affectedProductId =
//                                                orderSubmittedEvent
//                                                        .getEventBody()
//                                                        .get("OrderId")
//                                                        .asInt();
//                //                        return affectedProductId
//                                                == paymentEvent
//                                                        .getEventBody()
//                                                        .get("OrderId")
//                                                        .asInt();
//                //                    }
////
//                //                    return false;
//                //                }
//                //            })
//        //            .within(Time.seconds(5));
//}
