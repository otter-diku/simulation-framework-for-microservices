package org.myorg.flinkinvariants.invariantcheckers.lakeside;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;

import java.time.Duration;

public class LakesidePrintJob {

    private static final String TOPIC_1 = "insurance-quote-request-event-queue";
    private static final String TOPIC_2 = "customer-decision-event-queue";
    private static final String TOPIC_3 = "insurance-quote-response-event-queue";
    private static final String TOPIC_4 = "insurance-quote-expired-event-queue";
    private static final String TOPIC_5 = "policy-created-event-queue";
    private static final String GROUP_ID = "Lakeside-print-job";

    private static final int MAX_LATENESS_OF_EVENT = 20;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource1 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_1, GROUP_ID);
        streamSource1.map(event -> event.Type + ": " + event.Content.toString()).print();

        var streamSource2 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_2, GROUP_ID);
        streamSource2.map(event -> event.Type + ": " + event.Content.toString()).print();

        var streamSource3 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_3, GROUP_ID);
        streamSource3.map(event -> event.Type + ": " + event.Content.toString()).print();

        var streamSource4 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_4, GROUP_ID);
        streamSource4.map(event -> event.Type + ": " + event.Content.toString()).print();

        var streamSource5 = KafkaReader.GetDataStreamSourceLakeside(env, TOPIC_5, GROUP_ID);
        streamSource5.map(event -> event.Type + ": " + event.Content.toString()).print();


        env.execute("Lakeside print job");
    }
}
