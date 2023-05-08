package org.myorg.flinkinvariants.invariantcheckers.lakeside;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.flinkinvariants.events.InvariantViolationEvent;
import org.myorg.flinkinvariants.sinks.SeqSink;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TimelyPolicyCreation {

    private static final String TOPIC_CUSTOMER_DECISION = "customer-decision-event-queue";
    private static final String TOPIC_POLICY_CREATED = "policy-created-event-queue";


    private static final String GROUP_ID = "Lakeside-print-job";

    private static final int MAX_LATENESS_OF_EVENT = 1;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var streamSourceCustomer = KafkaReader.GetEventDataStreamSource(env, TOPIC_CUSTOMER_DECISION, GROUP_ID);
        var streamSourcePolicy = KafkaReader.GetEventDataStreamSource(env, TOPIC_POLICY_CREATED, GROUP_ID);

        var combinedStream = streamSourceCustomer.union(streamSourcePolicy)
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                .withTimestampAssigner((event, timestamp) -> event.Content.get("date").asLong()));

        var timelyCreationPattern = Pattern.<Event>begin("customerAccepted")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(Event event) {
                        return event.Type.equals(TOPIC_CUSTOMER_DECISION) && event.Content.get("quoteAccepted").asBoolean();
                    }
                }).notFollowedBy("policyCreated")
                .where(new IterativeCondition<Event>() {
                    @Override
                    public boolean filter(Event event, Context<Event> context) throws Exception {
                        if (!event.Type.equals(TOPIC_POLICY_CREATED)) {
                            return false;
                        }
                        var acceptedEvent = context.getEventsForPattern("customerAccepted").iterator().next();
                        return event.Content.get("insuranceQuoteRequestId").asInt()
                                == acceptedEvent.Content.get("insuranceQuoteRequestId").asInt();
                    }
                }).within(Time.milliseconds(40));


        DataStream<InvariantViolationEvent> acceptedWithToLateCreationStream = CEP.pattern(combinedStream, timelyCreationPattern)
                .inEventTime()
                .process(new PatternProcessFunction<Event, InvariantViolationEvent>() {
                    @Override
                    public void processMatch(Map<String, List<Event>> map, Context context, Collector<InvariantViolationEvent> collector) {
                        var event = map.get("customerAccepted").iterator().next();
                        collector.collect(new InvariantViolationEvent(Instant.now().toString(),
                                "{InvariantName} invariant violated",
                                Collections.singletonMap("InvariantName", event.Type)));
                    }
                });
        acceptedWithToLateCreationStream.addSink(new SeqSink());

        env.execute("Lakeside print job");
    }
}
