package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.Event;

import java.util.List;
import java.util.Map;

public class InvariantTemplate {
    private static final int MAX_LATENESS_OF_EVENT = 5;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment()
                .setParallelism(1);

        var streamSource = KafkaReader.GetEventDataStreamSource(env);

        CheckInvariant(env, streamSource, new PrintSinkFunction<>());
    }

    public static void CheckInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction) throws Exception {

        var patternStream = CEP.pattern(input, Invariant);
        var matches = patternStream
                .inEventTime()
                .process(new PatternProcessFunction<Event, String>() {
                    @Override
                    public void processMatch(Map<String, List<Event>> map, Context context, Collector<String> collector) {
                        collector.collect(map.toString());
                    }
                })
                .addSink(sinkFunction);

        env.execute("???");
    }

    public static Pattern<Event, ?> Invariant = Pattern.<Event>begin("???")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) {
                    // simply replace string type of first event
                    return event.Type.equals("{{}}");
                }
            })
            .notFollowedBy("???")
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(Event event, Context<Event> context) throws Exception {
                    // if multiple equalities with different events

                    if (!(event.Type.equals("???") || event.Type.equals("????")))
                    {
                        return false;
                    }

                    // Check equality of values
                    for (var specialEvent : context.getEventsForPattern("???")) {

                        var affectedProductId = event.Content.get("OrderId").asInt();
                        return affectedProductId == specialEvent.Content.get("OrderId").asInt();
                    }

                    return false;
                }
            })
            .within(Time.seconds(5));

    /* Invariant for:
       EVENTS (E_1 e1, E_2 e2, E_3 e3)
       WITHIN 5 min
     */
    public static Pattern<Event, ?> ExampleInvariant0 = Pattern.<Event>begin("E_1")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_1");
                }
            })
            .notFollowedBy("E_2")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_2");
                }
            })
            .notFollowedBy("E_3")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_3");
                }
            })
            .within(Time.seconds(5));

    /* Invariant for:
       EVENTS (E_1 e1, E_2 e2, E_3 e3)
       WHERE e1.id = e2.id AND e2.id = e3.id
       WITHIN 5 min
     */
    public static Pattern<Event, ?> ExampleInvariant = Pattern.<Event>begin("E_1")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_1");
                }
            })
            .notFollowedBy("E_2")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_2");
                }
            })
            .notFollowedBy("E_3")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_3");
                }
            })
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(Event event, Context<Event> context) throws Exception {

                    var e1 = context.getEventsForPattern("E_1").iterator().next();
                    var e2 = context.getEventsForPattern("E_2").iterator().next();
                    var e3 = event;


                    var e1_id = e1.Content.get("id").asText();
                    var e2_id = e2.Content.get("id").asText();
                    var e3_id = e3.Content.get("id").asText();

                    return e1_id.equals(e2_id) && e2_id.equals(e3_id);
                }
            })
            .within(Time.seconds(5));

    /* Invariant for:
           EVENTS (E_1 e1, E_2 e2, E_3 e3)
           WHERE e1.id = e2.id OR e2.id = e3.id
           WITHIN 5 min
         */
    public static Pattern<Event, ?> ExampleInvariant2 = Pattern.<Event>begin("E_1")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_1");
                }
            })
            .notFollowedBy("E")
            .where(new SimpleCondition<Event>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_2") || event.Type.equals("E_3");
                }
            })
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(Event event, Context<Event> context) throws Exception {

                    var e1 = context.getEventsForPattern("E_1").iterator().next();

                    var e1_id = e1.Content.get("id").asText();
                    var e_id = event.Content.get("id").asText();

                    return e1_id.equals(e_id);
                }
            })
            .within(Time.seconds(5));


         /* Invariant for:
           EVENTS (E_1 e1, E_2 e2, E_3 e3)
           WHERE e1.id = e2.id AND (e2.id = e3.id OR e1.other = e3.other)
           WITHIN 5 min
         */
         public static Pattern<Event, ?> ExampleInvariant3 = Pattern.<Event>begin("E_1")
                 .where(new SimpleCondition<>() {
                     @Override
                     public boolean filter(Event event) throws Exception {
                         return event.Type.equals("E_1");
                     }
                 })
                 .notFollowedBy("E_2")
                 .where(new SimpleCondition<>() {
                     @Override
                     public boolean filter(Event event) throws Exception {
                         return event.Type.equals("E_2");
                     }
                 })
                 .notFollowedBy("E_3")
                 .where(new SimpleCondition<>() {
                     @Override
                     public boolean filter(Event event) throws Exception {
                         return event.Type.equals("E_3");
                     }
                 })
                 .where(new IterativeCondition<>() {
                     @Override
                     public boolean filter(Event event, Context<Event> context) throws Exception {

                         var e1 = context.getEventsForPattern("E_1").iterator().next();
                         var e2 = context.getEventsForPattern("E_2").iterator().next();
                         var e3 = event;


                         var e1_id = e1.Content.get("id").asText();
                         var e2_id = e2.Content.get("id").asText();
                         var e3_id = e3.Content.get("id").asText();

                         return e1_id.equals(e2_id) &&
                                 (e2.Content.get("id").asText().equals(e3.Content.get("id").asText())
                             ||   e1.Content.get("other").asText().equals(e3.Content.get("other").asText()));

                     }
                 })
                 .within(Time.seconds(5));

    /* Invariant for:
               EVENT SEQ (E_1 e1, E_2 e2, E_3 e3)
               WITHIN 5 sec
             */
    public static Pattern<Event, ?> ExampleInvariant4 = Pattern.<Event>begin("E_1")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_1");
                }
            })
            .notFollowedBy("E_2")
            .where(new SimpleCondition<Event>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_2");
                }
            })
            .notFollowedBy("E_3")
            .where(new SimpleCondition<Event>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("E_3");
                }
            })
            .within(Time.seconds(5));
}
