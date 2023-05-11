package org.shared;

import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.invariantchecker.events.Event;

public interface InvariantChecker {
    void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
//            Pattern<Event, ?> invariant,
            SinkFunction<String> sinkFunction)
            throws Exception;
}
