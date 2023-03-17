package org.myorg.flinkinvariants;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.myorg.flinkinvariants.events.Event;

public class CEPExampleJob {

    public static void main(String[] args) throws Exception {
        String broker = "localhost:29092";
        String topic = "eshop_event_bus";
        String groupId = "flink-invariant-checker";

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Create input sequence
        DataStream<Event> input = env.fromElements(
                new Event(1, "a"),
                new Event(2, "c"),
                new Event(1, "b1"),
                new Event(3, "b2"),
                new Event(4, "d"),
                new Event(4, "b3")
        );

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start").where(new SimpleCondition<Event>() {
            @Override
            public boolean filter(Event value){
                return value.getName().startsWith("b");
            }
        });

        DataStream<String> result =
                CEP.pattern(input, pattern)
                        .inProcessingTime()
                        .flatSelect(
                                (p, o) -> {
                                    StringBuilder builder = new StringBuilder();
                                    builder.append(p.get("start").get(0));
                                    o.collect(builder.toString());
                                },
                                Types.STRING);

        result.print();

        // Execute program, beginning computation.
        env.execute("CEP Example Job");
    }
}
