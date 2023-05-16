package org.invariantgenerator.invariantlanguage;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.invarianttranslator.events.*;
import org.shared.*;

public class TestInvariantTemplate implements InvariantChecker {

    public void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
//            Pattern<Event, ?> invariant,
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

    public Pattern<Event, ?> invariant;
}
