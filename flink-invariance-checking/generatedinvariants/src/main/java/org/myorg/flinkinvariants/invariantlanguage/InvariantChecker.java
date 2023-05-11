package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.myorg.flinkinvariants.events.Event;

public interface InvariantChecker {
    public  void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception;
}
