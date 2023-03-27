package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.api.common.eventtime.TimestampAssigner;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.myorg.flinkinvariants.FileReader;
import org.myorg.flinkinvariants.FromElementsReader;
import org.myorg.flinkinvariants.KafkaReader;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.myorg.flinkinvariants.events.OrderPaymentFailedIntegrationEvent;
import org.myorg.flinkinvariants.events.OrderPaymentSucceededIntegrationEvent;
import org.myorg.flinkinvariants.patterns.LackingPaymentInvariant;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class LackingPaymentEventInvariantChecker {
    final static OutputTag<String> outputTag = new OutputTag<String>("lacking-payments"){};

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // var streamSource = FileReader.GetDataStreamSource(env, "/src/lacking_payment_1.json");
        var dataStreamSource = KafkaReader.GetDataStreamSource(env);
        var streamSource = dataStreamSource.filter(new FilterFunction<EshopRecord>() {
            @Override
            public boolean filter(EshopRecord record) throws Exception {
                if (record.EventName.equals(OrderPaymentSucceededIntegrationEvent.EventName) || record.EventName.equals(OrderPaymentFailedIntegrationEvent.EventName)) {
                    return false;
                }
                return true;
            }
        });

        // var streamSource = FromElementsReader.GetDataStreamSource(env);

        // streamSource.print().setParallelism(1);
        var patternStream = CEP.pattern(streamSource, LackingPaymentInvariant.InvariantPattern);

        var matches = patternStream
                .inProcessingTime()
                .process(new MyPatternProcessFunction());
        matches.print().setParallelism(1);

        var partialMatches = matches.getSideOutput(outputTag);
        partialMatches.print().setParallelism(1);

        System.out.println("Started CEP query for Price Changed Invariant..");
        // System.out.println(env.getExecutionPlan());
        env.execute("Flink Eshop Product Price Changed Invariant");
    }


    public static class MyEventTimestampAssigner implements TimestampAssigner<EshopRecord> {

        @Override
        public long extractTimestamp(EshopRecord record, long l) {
            return record.getTimestamp();
        }
    }

    public static class MyPatternProcessFunction extends PatternProcessFunction<EshopRecord, String> implements TimedOutPartialMatchHandler<EshopRecord> {

        @Override
        public void processMatch(Map map, Context context, Collector collector) throws Exception {
            collector.collect("Correct Behaviour: " + map.toString());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<EshopRecord>> map, Context context) throws Exception {
            // timed out match means we are missing a payment event
            var orderSubmittedEvent = map.get("orderSubmitted").get(0);
            context.output(outputTag, "Violation missing payment event for: " + orderSubmittedEvent.toString());
        }
    }

}
