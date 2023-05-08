package org.myorg.flinkinvariants.invariantcheckers;

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
import org.myorg.flinkinvariants.events.Event;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class TestInvariant {

    private static final int MAX_LATENESS_OF_EVENT = 1;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);

        String groupId = "invariant-checker";

var streamSource0 = KafkaReader.GetEventDataStreamSource(env, "customer-decision-event-queue", groupId);
var streamSource1 = KafkaReader.GetEventDataStreamSource(env, "policy-created-event-queue", groupId);


DataStream<Event> inputStream = streamSource0.union(streamSource1);

        var streamSource =
                inputStream.assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Event>forBoundedOutOfOrderness(
                                        Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                                .withTimestampAssigner(
                                        (event, timestamp) -> event.Content.get("date").asLong()));

        var test = streamSource.filter(event ->
                false || event.Type.equals("test")
                || event.Type.equals("")
        ).setParallelism(1);

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
public static Pattern<Event, ?> invariant = Pattern.<Event>begin("customer-decision-event")
.where(new SimpleCondition<>() {
    @Override
    public boolean filter(Event event) throws Exception {
        return event.Type.equals("customer-decision-event");
    }
}).notFollowedBy("policy-created-event")
.where(new SimpleCondition<Event>() {
    @Override
    public boolean filter(Event event) throws Exception {
        return event.Type.equals("policy-created-event");
    }
}).where(new IterativeCondition<>() {
    @Override
    public boolean filter(Event event, Context<Event> context) throws Exception {
return context.getEventsForPattern("cd").iterator().next().Content.get("insuranceQuoteRequestId").toString()
.equals(event.Content.get("insuranceQuoteRequestId").toString()
) && context.getEventsForPattern("cd").iterator().next().Content.get("quoteAccepted").toString()
.equals("true");    }
}).within(Time.milliseconds(10));
}
