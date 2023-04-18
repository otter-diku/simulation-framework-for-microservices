package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.events.Event;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class InvariantTemplate {

    private static final int MAX_LATENESS_OF_EVENT = 1;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);

        String groupId = "invariant-checker";

        // STREAMS

        DataStream<Event> inputStream = null;

        var streamSource =
                inputStream.assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Event>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                                .withTimestampAssigner(
                                        (event, timestamp) -> event.Content.get("date").asLong()));
        CheckInvariant(env, streamSource, new PrintSinkFunction<>());
    }

    public static void CheckInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception {

        var patternStream = CEP.pattern(input, invariant);
        var matches =
                patternStream
                        .inEventTime()
                        .process(
                                new PatternProcessFunction<Event, String>() {
                                    @Override
                                    public void processMatch(
                                            Map<String, List<Event>> map,
                                            Context context,
                                            Collector<String> collector)
                                            throws Exception {
                                        collector.collect(map.toString());
                                    }
                                })
                        .addSink(sinkFunction);

        env.execute("invariant");
    }

    // INVARIANT
    public static Pattern<Event, ?> invariant;
}
