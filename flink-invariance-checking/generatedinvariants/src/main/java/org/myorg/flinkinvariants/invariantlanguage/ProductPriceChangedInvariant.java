package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.*;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.Event;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProductPriceChangedInvariant  {

    private static final int MAX_LATENESS_OF_EVENT = 2;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);
        checkInvariant(env, getDataStream(env), new PrintSinkFunction<>());
    }

    public static DataStream<Event> getDataStream(StreamExecutionEnvironment env) {
        // read from config file
        // String broker = "localhost:29092";
        String broker = "broker:9092";
        String topic1 = "eshop_event_bus"; // --> defined in schema
        String groupId = "flink-invariant-checker";

        var kafkaSource1 = KafkaReader.getEventKafkaSource(broker, topic1, groupId);
        var stream1 = env.fromSource(kafkaSource1, WatermarkStrategy.noWatermarks(), "Kafka Source");

        // possibly many streams need to be combined
        var stream = stream1;

        // Assign watermark strategy (event timestamps or kafka timestamps)
        // No timestamps on events (using Kafka)
        var streamWithWatermark = stream.assignTimestampsAndWatermarks(
                WatermarkStrategy.<Event>forBoundedOutOfOrderness(
                                Duration.ofSeconds(MAX_LATENESS_OF_EVENT)));

        // Assuming event has "ts" -> timestamp as long, maybe can support parsing standard time formats!
        // stream.assignTimestampsAndWatermarks(WatermarkStrategy
        //         .<Event>forBoundedOutOfOrderness(Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
        //         .withTimestampAssigner((event, timestamp) -> event.Content.get("ts").asLong()));


        // filter by relevant events
        var filteredStream = streamWithWatermark.filter(e ->
                e.Type.equals("ProductPriceChangedIntegrationEvent") ||
                e.Type.equals("ProductBoughtIntegrationEvent")
        ).setParallelism(1);
        return filteredStream;
    }

    public static void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception {

        var patternStream = CEP.pattern(input, invariant);
        var matches =
                patternStream
                        .inProcessingTime()
                        .process(new MyPatternProcessFunction());
        matches.getSideOutput(outputTag).addSink(sinkFunction);
        matches.addSink(sinkFunction);


        matches.sinkTo(
                KafkaSink.<String>builder()
                        .setBootstrapServers(
                                "broker:9092")
                        .setRecordSerializer(
                                KafkaRecordSerializationSchema.builder()
                                        .setTopic("product-price-changed-violations")
                                        .setKafkaValueSerializer(StringSerializer.class)
                                        .build())
                        .build());

        env.execute("Test invariant");
    }

    private static final OutputTag<String> outputTag = new OutputTag<>("timeoutMatch") {};

    public static class MyPatternProcessFunction
            extends PatternProcessFunction<Event, String>
            implements TimedOutPartialMatchHandler<Event> {
        @Override
        public void processMatch(Map<String, List<Event>> map, Context context, Collector<String> collector) {
            if(!((__9480782e(map)))) { collector.collect(map.toString()); }
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> map, Context context) {
            return;
        }

    }
    static boolean __9480782e(Map<String, List<Event>> map) {
        Optional<List<Double>> lhs;
        try {

            var temp = map.get("[pc1]").stream()
                    .filter(e -> e.Type.equals("ProductPriceChangedIntegrationEvent"))
                    .map(e -> e.Content.get("NewPrice").asDouble())
                    .collect(Collectors.toList());
            lhs = Optional.ofNullable(temp);
        } catch (Exception e) {
            lhs = Optional.ofNullable(null);
        }


        Optional<List<Double>> rhs;
        try {

            var temp = map.get("[pb]").stream()
                    .filter(e -> e.Type.equals("ProductBoughtIntegrationEvent"))
                    .map(e -> e.Content.get("Price").asDouble())
                    .collect(Collectors.toList());
            rhs = Optional.ofNullable(temp);
        } catch (Exception e) {
            rhs = Optional.ofNullable(null);
        }


        if (lhs.isPresent() && rhs.isPresent()) {
            if (lhs.get().isEmpty() || rhs.get().isEmpty()) {return false;}
            for (var elemL : lhs.get()) {
                for (var elemR : rhs.get()) {
                    if(!(Double.compare(elemL,elemR) == 0)) {
                        return false;
                    }
                }
            }
            return true;

        } else {
            return false;
        }

    };



    public static Pattern<Event, ?> invariant =
            Pattern.<Event>begin("[pc1]").where(SimpleCondition.of(e -> e.Type.equals("ProductPriceChangedIntegrationEvent")
                    ))
                    .notFollowedBy("[pc2]")
                    .where(SimpleCondition.of(e -> e.Type.equals("ProductPriceChangedIntegrationEvent")
                    ))
                    .where(
                            new IterativeCondition<>() {
                                @Override
                                public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
                                    return __7fe7e5b3(event, context);
                                }
                            })
                    .followedByAny("[pb]")
                    .where(SimpleCondition.of(e -> e.Type.equals("ProductBoughtIntegrationEvent")
                    ))
                    .where(
                            new IterativeCondition<>() {
                                @Override
                                public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
                                    return __7eb7f6cc(event, context);
                                }
                            })
                    .within(Time.minutes(2));;
    static boolean __7fe7e5b3(Event event, IterativeCondition.Context<Event> context) {
        Optional<Double> lhs;
        try {

            var temp = context.getEventsForPattern("[pc1]")
                    .iterator()
                    .next();
            lhs = Optional.of(temp.Content.get("ProductId").asDouble());
        } catch (Exception e) {
            lhs = Optional.ofNullable(null);
        }


        Optional<Double> rhs;
        try {

            var temp = event;
            rhs = Optional.of(temp.Content.get("ProductId").asDouble());
        } catch (Exception e) {
            rhs = Optional.ofNullable(null);
        }


        if (lhs.isPresent() && rhs.isPresent()) {
            return Double.compare(lhs.get(),rhs.get()) == 0;
        } else {
            return false;
        }

    };

    static boolean __7eb7f6cc(Event event, IterativeCondition.Context<Event> context) {
        Optional<Double> lhs;
        try {

            var temp = context.getEventsForPattern("[pc1]")
                    .iterator()
                    .next();
            lhs = Optional.of(temp.Content.get("ProductId").asDouble());
        } catch (Exception e) {
            lhs = Optional.ofNullable(null);
        }


        Optional<Double> rhs;
        try {

            var temp = event;
            rhs = Optional.of(temp.Content.get("ProductId").asDouble());
        } catch (Exception e) {
            rhs = Optional.ofNullable(null);
        }


        if (lhs.isPresent() && rhs.isPresent()) {
            return Double.compare(lhs.get(),rhs.get()) == 0;
        } else {
            return false;
        }

    };


}
