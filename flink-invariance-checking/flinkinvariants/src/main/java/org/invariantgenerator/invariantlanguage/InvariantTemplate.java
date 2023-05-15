package org.invariantgenerator.invariantlanguage;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.*;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.invariantchecker.events.*;
import org.shared.*;
import org.invariantchecker.datastreamsourceproviders.KafkaReader;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class InvariantTemplate {

    // ${main}

    // ${DataStreamCode}

    public static void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception {

        var patternStream = CEP.pattern(input, invariant);
        var matches =
                patternStream
                        .inProcessingTime()
                ;// ${process}

        env.execute("Test invariant");
    }

    // ${MyPatternProcessFunction}

    public static Pattern<Event, ?> invariant;
}
