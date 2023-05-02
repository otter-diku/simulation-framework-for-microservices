package org.myorg.flinkinvariants.invariantcheckers;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.nfa.aftermatch.AfterMatchSkipStrategy;
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

public class Invariant {

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

        CheckInvariant(env, streamSource, new PrintSinkFunction<>(), InvariantPattern);
    }

    public static void CheckInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<String> sinkFunction, Pattern<EShopIntegrationEvent, ?> pattern)
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

        var patternStream = CEP.pattern(filteredStream, pattern);
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
                                        System.out.println(map.toString());
                                        collector.collect(map.toString());
                                    }
                                })
                        .addSink(sinkFunction);

        env.execute("Flink Eshop Product Price Changed Invariant");
    }

    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern =
            Pattern.<EShopIntegrationEvent>begin("firstPriceChange")
                    .where(SimpleCondition.of(e ->
                            e.getEventName().equals
                                    ("ProductPriceChangedIntegrationEvent")))
                    .followedByAny("checkout").oneOrMore().greedy()
                    .where(SimpleCondition.of(e ->
                            e.getEventName().equals(
                                    "UserCheckoutAcceptedIntegrationEvent"
                            )))
                    .where(new IterativeCondition<EShopIntegrationEvent>() {
                        @Override
                        public boolean filter(EShopIntegrationEvent eShopIntegrationEvent, Context<EShopIntegrationEvent> context) throws Exception {
                            var checkouts = context.getEventsForPattern("checkout");
                            for (var c : checkouts) {
                                System.out.println(c);
                            }
                            return true;
                        }
                    });

    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern2 =
            Pattern.<EShopIntegrationEvent>begin("c", AfterMatchSkipStrategy.skipPastLastEvent()).oneOrMore().greedy()
                    .where(SimpleCondition.of(e ->
                            e.getEventName().equals(
                                    "UserCheckoutAcceptedIntegrationEvent"
                            )));
}
