package org.myorg.flinkinvariants.invariantcheckers.lakeside;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.Event;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class SortingWithCEP {

        private static final String TOPIC_1 = "insurance-quote-request-event-queue";
        private static final String TOPIC_2 = "customer-decision-event-queue";
        private static final String TOPIC_3 = "insurance-quote-response-event-queue";
        private static final String TOPIC_4 = "insurance-quote-expired-event-queue";
        private static final String TOPIC_5 = "policy-created-event-queue";
        private static final String GROUP_ID = "Lakeside-print-job";

        private static final int MAX_LATENESS_OF_EVENT = 20;

        public static void main(String[] args) throws Exception {
            final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
            env.setParallelism(1);

            var streamSource1 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_1, GROUP_ID);
            var streamSource2 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_2, GROUP_ID);
            var streamSource3 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_3, GROUP_ID);
            var streamSource4 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_4, GROUP_ID);
            var streamSource5 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_5, GROUP_ID);

            var combinedStream = streamSource1.union(streamSource2, streamSource3, streamSource4, streamSource5)
                    .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                            .withIdleness(Duration.ofSeconds(1))
                            .withTimestampAssigner((event, timestamp) -> event.Content.get("date").asLong()));

            var sortPattern = Pattern.<Event>begin("everything")
                    .where(new SimpleCondition<>() {
                        @Override
                        public boolean filter(Event event) {
                            return true;
                        }
                    });
            DataStream<Event> sortedStream = CEP.pattern(combinedStream, sortPattern)
                    .inEventTime()
                    .process(new PatternProcessFunction<Event, Event>() {
                        @Override
                        public void processMatch(Map<String, List<Event>> map, Context context, Collector<Event> collector) {
                            collector.collect(map.get("everything").get(0));
                        }
                    });
            sortedStream.map(event -> event.Content.toString()).print();

            env.execute("Lakeside print job");
        }
}
