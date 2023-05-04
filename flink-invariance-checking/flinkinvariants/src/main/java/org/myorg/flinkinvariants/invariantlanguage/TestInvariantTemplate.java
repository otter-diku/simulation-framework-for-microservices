package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.events.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TestInvariantTemplate implements InvariantChecker {

    public void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception {

        var patternStream = CEP.pattern(input, invariant);
        var matches =
                patternStream
                        .inProcessingTime()
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

        env.execute("Test invariant");
    }

    public Pattern<Event, ?> invariant;
}
