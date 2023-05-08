import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.junit.ClassRule;
import org.junit.Test;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.InvariantViolationEvent;
import org.myorg.flinkinvariants.events.Event;

import java.io.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.myorg.flinkinvariants.invariantcheckers.Invariant.*;
import static org.myorg.flinkinvariants.invariantcheckers.eshop.ProductOversoldInvariantChecker.CheckOversoldInvariant;
import static org.myorg.flinkinvariants.invariantcheckers.eshop.ProductPriceChangedInvariantChecker.CheckProductPriceChangedInvariant;

public class InvariantsTest {

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @Test
    public void testFlinkBehavior_1() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = env.fromElements("a", "b1", "b2", "b3", "c");
        var pattern = Pattern.<String>begin("a")
                .where(SimpleCondition.of(e -> e.equals("a")))
                .followedByAny("b").oneOrMore()
                .where(SimpleCondition.of(e -> e.startsWith("b")))
                .followedBy("c")
                .where(SimpleCondition.of(e -> e.equals("c")))
                .oneOrMore().greedy();

        var patternStream = CEP.pattern(streamSource, pattern).inProcessingTime().process(
                new PatternProcessFunction<String, Object>() {
                    @Override
                    public void processMatch(Map<String, List<String>> map, Context context, Collector<Object> collector)
                            throws Exception {
                        collector.collect(map.toString());
                    }
                });

        patternStream.print();
        env.execute("name");
    }


    @Test
    public void testFlinkBehavior_2() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = env.fromElements("a", "b1", "b2", "b3", "c1", "c2", "c3");
        var pattern = Pattern.<String>begin("a")
                .where(SimpleCondition.of(e -> e.equals("a")))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.startsWith("b")))
                .oneOrMore().greedy()
                .followedBy("c")
                .where(SimpleCondition.of(e -> e.startsWith("c")))
                .oneOrMore().greedy();


        var patternStream = CEP.pattern(streamSource, pattern).inProcessingTime().process(
                new PatternProcessFunction<String, Object>() {
                    @Override
                    public void processMatch(Map<String, List<String>> map, Context context, Collector<Object> collector)
                            throws Exception {
                        collector.collect(map.toString());
                    }
                });

        patternStream.print();
        env.execute("a");
    }

    @Test
    public void testFlinkBehavior_3() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = env.fromElements("a", "b", "c", "x", "y", "a", "b",  "c","x", "y", "a", "b", "c", "x", "y", "a", "b", "c", "x", "y", "a", "b",  "c","x", "y", "a", "b", "c", "x", "y", "a", "b",  "c","x", "y");
        var pattern = Pattern.<String>begin("a")
                .where(SimpleCondition.of(e -> e.equals("a")))
                .followedBy("b")
                .where(SimpleCondition.of(e -> e.equals("b")))
                .notFollowedBy("c")
                .where(SimpleCondition.of(e -> e.equals("c")))
                .within(Time.milliseconds(1));


        // SEQ (a, b, !c)
        // SEQ {a, b, ...... TIME OUT}
        // SEQ {a, ... TIME OUT}
        // {a, b, TIME OUT}
        // {a, TIME OUT}

        var patternStream = CEP.pattern(streamSource, pattern).inProcessingTime().process(
                new MyPatternProcessFunction());

        patternStream.print();
        var ds = patternStream.getSideOutput(outputTag);
        ds.print();

        env.execute("a");
    }

    static final OutputTag<String> outputTag = new OutputTag<>("test") {};


    public static class MyPatternProcessFunction
            extends PatternProcessFunction<String, String>
            implements TimedOutPartialMatchHandler<String> {

        @Override
        public void processMatch(Map map, Context context, Collector collector) {
            collector.collect(map.toString());

        }

        @Override
        public void processTimedOutMatch(
                Map<String, List<String>> map, Context context) {
            context.output(
                    outputTag,
                    "timed out match:" + map.toString());
        }
    }


    @Test
    public void testProductPriceChangedInvariant1() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/product_price_changed_invariant_1.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        var violations = ViolationSink.values;
        assertEquals(2, violations.size());
/*
        var productPriceChangedEventId1 = "1e693c62-c349-447f-87df-6be170c099fa";
        var productPriceChangedEventId2 = "2e693c62-c349-447f-87df-6be170c099fa";
        var userCheckoutEventId = "421b7801-1014-4747-80d3-8097343c6e0e";

        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId1)
                                                && s.contains(userCheckoutEventId)));
        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId2)
                                                && s.contains(userCheckoutEventId)));
*/
    }

    @Test
    public void testProductPriceChangedInvariant2() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/product_price_changed_invariant_2.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        var violations = ViolationSink.values;
        assertEquals(3, violations.size());

/*        var productPriceChangedEventId1 = "0e693c62-c349-447f-87df-6be170c099fa";
        var userCheckoutEventId1 = "021b7801-1014-4747-80d3-8097343c6e0e";
        var userCheckoutEventId2 = "121b7801-1014-4747-80d3-8097343c6e0e";
        var userCheckoutEventId3 = "321b7801-1014-4747-80d3-8097343c6e0e";

        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId1)
                                                && s.contains(userCheckoutEventId1)));
        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId1)
                                                && s.contains(userCheckoutEventId2)));
        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId1)
                                                && s.contains(userCheckoutEventId3)));*/
    }

    @Test
    public void testProductPriceChangedInvariant2_1() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/test.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckInvariant(env, streamSource, new ViolationSinkString(), InvariantPattern);

        var violations = ViolationSink.values;
    }
    @Test
    public void testProductPriceChangedInvariant2_2() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/test.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckInvariant(env, streamSource, new ViolationSinkString(), InvariantPattern2);

        var violations = ViolationSink.values;
    }
    @Test
    public void testProductPriceChangedInvariant3() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // This input tests if event-time is working correctly
        // by having the checkout come first in the file but its timestamp
        // is after first two price changed events
        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/product_price_changed_invariant_3.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        var violations = ViolationSink.values;
        assertEquals(2, violations.size());
/*        var productPriceChangedEventId1 = "1e693c62-c349-447f-87df-6be170c099fa";
        var productPriceChangedEventId2 = "2e693c62-c349-447f-87df-6be170c099fa";
        var userCheckoutEventId = "421b7801-1014-4747-80d3-8097343c6e0e";

        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId1)
                                                && s.contains(userCheckoutEventId)));
        assertTrue(
                violations.stream()
                        .anyMatch(
                                s ->
                                        s.contains(productPriceChangedEventId2)
                                                && s.contains(userCheckoutEventId)));*/
    }

    @Test
    public void testProductPriceChangedInvariant4() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/product_price_changed_invariant_4.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        assertTrue(ViolationSink.values.isEmpty());
    }

//    @Test
//    public void testLackingPaymentInvariant1() throws Exception {
//        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//
//        var fileSource = new TimedFileSource("/src/lacking_payment_1.json", 100);
//
//        var streamSource =
//                env.addSource(fileSource)
//                        .assignTimestampsAndWatermarks(
//                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
//                                                Duration.ofSeconds(20))
//                                        .withTimestampAssigner(
//                                                (event, timestamp) -> event.getEventTime()));
//
//        // values are collected in a static variable
//        ViolationSink.values.clear();
//
//        CheckLackingPaymentInvariant(env, streamSource, new ViolationSink());
//
//        var violations = ViolationSink.values;
//        assertEquals(1, violations.size());
///*        assertTrue(violations.get(0).contains("\"OrderId\":9883"));*/
//    }

//    @Test
//    public void testLackingPaymentInvariant2() throws Exception {
//        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//
//        var fileSource = new TimedFileSource("/src/lacking_payment_2.json", 100);
//
//        var streamSource =
//                env.addSource(fileSource)
//                        .assignTimestampsAndWatermarks(
//                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
//                                                Duration.ofSeconds(20))
//                                        .withTimestampAssigner(
//                                                (event, timestamp) -> event.getEventTime()));
//
//        // values are collected in a static variable
//        ViolationSink.values.clear();
//
//        CheckLackingPaymentInvariant(env, streamSource, new ViolationSink());
//
//        var violations = ViolationSink.values;
//        // TODO: timed out events violation are published twice for now just remove duplicates in
//        // operator
//        assertEquals(1, violations.size());
///*        assertTrue(violations.get(0).contains("\"OrderId\":9881"));*/
//    }

//    @Test
//    public void testProductOversoldInvariant1() throws Exception {
//        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//
//        var streamSource =
//                FileReader.GetDataStreamSource(env, "/src/oversold_1.json")
//                        .assignTimestampsAndWatermarks(
//                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
//                                                Duration.ofSeconds(20))
//                                        .withTimestampAssigner(
//                                                (event, timestamp) -> event.getEventTime()));
//
//        // values are collected in a static variable
//        ViolationSink.values.clear();
//
//        CheckOversoldInvariant(env, streamSource, new ViolationSink());
//
//        var violations = ViolationSink.values;
//        assertEquals(1, violations.size());
///*
//        assertTrue(
//                violations
//                        .get(0)
//                        .contains(
//                                "Violation: stock not sufficient for ProductId: 42, current stock: 10, units bought: 20"));
//*/
//    }

    @Test
    public void testProductOversoldInvariant2() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/oversold_2.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckOversoldInvariant(env, streamSource, new ViolationSink());

        var violations = ViolationSink.values;
        assertEquals(0, violations.size());
    }

    @Test
    public void testProductOversoldInvariant3() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/oversold_3.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(20))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        // values are collected in a static variable
        ViolationSink.values.clear();

        CheckOversoldInvariant(env, streamSource, new ViolationSink());

        var violations = ViolationSink.values;
        assertEquals(0, violations.size());
    }


    private static class ViolationSink implements SinkFunction<InvariantViolationEvent> {

        // must be static
        public static final List<InvariantViolationEvent> values = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(InvariantViolationEvent value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }

    private static class ViolationSinkString implements SinkFunction<String> {

        // must be static
        public static final List<String> values = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(String value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }


    private static Event getEventFromString(String eventString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(eventString, Event.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    private static class TimedFileSource extends RichParallelSourceFunction<EShopIntegrationEvent> {

        private transient volatile boolean running = true;

        private final String filename;

        private final long waitTime;

        @Override
        public void open(Configuration parameters) {
            running = true;
        }

        public TimedFileSource(String filename, long waitTime) {
            this.filename = filename;
            this.waitTime = waitTime;
        }

        @Override
        public void run(SourceContext<EShopIntegrationEvent> sourceContext) throws Exception {
            var events = FileReader.GetEshopEventsFromFile(filename);

            for (var event : events) {
                sourceContext.collect(event);
            }
            // not canceling simulating unbounded stream
            Thread.sleep(waitTime);
        }

        @Override
        public void cancel() {
            running = false;
        }
    }
}
