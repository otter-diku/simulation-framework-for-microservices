package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.myorg.flinkinvariants.FileReader;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.myorg.flinkinvariants.patterns.LackingPaymentInvariant;

import java.util.List;
import java.util.Map;
import java.util.Scanner;


public class LackingPaymentEventNotFollowedInvariantChecker {
    final static OutputTag<String> outputTag = new OutputTag<String>("lacking-payments"){};

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = FileReader.GetDataStreamSource(env, "/src/lacking_payment_1.json");

        // streamSource.print().setParallelism(1);

        var patternStream = CEP.pattern(streamSource, LackingPaymentInvariant.InvariantPatternNotFollowedBy);
        var matches = patternStream
                .inProcessingTime()
                .process(new PatternProcessFunction<EshopRecord, String>() {
                    @Override
                    public void processMatch(Map<String, List<EshopRecord>> map, Context context, Collector<String> collector) throws Exception {
                        collector.collect(map.toString());
                    }
                });
        matches.print().setParallelism(1);

        System.out.println("Started CEP query for Price Changed Invariant..");
        env.execute("Flink Eshop Product Price Changed Invariant");
    }

}
