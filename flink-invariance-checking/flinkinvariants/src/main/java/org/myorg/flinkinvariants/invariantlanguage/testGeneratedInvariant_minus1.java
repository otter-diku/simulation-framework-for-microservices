package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.events.Event;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class testGeneratedInvariant_minus1 implements InvariantChecker {

    public void checkInvariant(
            StreamExecutionEnvironment env,
            DataStream<Event> input,
            SinkFunction<String> sinkFunction)
            throws Exception {

        var patternStream = CEP.pattern(input, invariant);
        var matches =
                patternStream
                        .inProcessingTime()
                        .process(
                                new PatternProcessFunction<Event, String>() {
                                    @Override
                                    public void processMatch(
                                            Map<String, List<Event>> map,
                                            Context context,
                                            Collector<String> collector)
                                            throws Exception {
                                        collector.collect(map.toString());
                                    }
                                })
                        .addSink(sinkFunction);

        env.execute("Test invariant");
    }

public Pattern<Event, ?> invariant = 
Pattern.<Event>begin("[a]").where(SimpleCondition.of(e -> e.Type.equals("A")
))
.notFollowedBy("[b]")
.where(SimpleCondition.of(e -> e.Type.equals("B")
))
    .where(
        new IterativeCondition<>() {
            @Override
            public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
            return __840b937e(event, context)||__bd71205b(event, context)&&(__da646e21(event, context)||(__bd71205b(event, context)));
    }
})
.followedByAny("[c]")
.where(SimpleCondition.of(e -> e.Type.equals("C")
))
    .where(
        new IterativeCondition<>() {
            @Override
            public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
            return __6f6dd694(event, context);
    }
})
    .where(
        new IterativeCondition<>() {
            @Override
            public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
            return __db69d7bf(event, context);
    }
})
.within(Time.seconds(1));;
boolean __840b937e(Event event, IterativeCondition.Context<Event> context) {
        Optional<String> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("id").asText());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        Optional<String> rhs;
try {

var temp = event;
    rhs = Optional.of(temp.Content.get("id").asText());
} catch (Exception e) {
    rhs = Optional.ofNullable(null);
}


            if (lhs.isPresent() && rhs.isPresent()) {
        return lhs.get().equals(rhs.get());
    } else {
        return false;
    }

};

boolean __bd71205b(Event event, IterativeCondition.Context<Event> context) {
        Optional<String> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        Optional<String> rhs;
try {

var temp = event;
    rhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    rhs = Optional.ofNullable(null);
}


            if (lhs.isPresent() && rhs.isPresent()) {
        return lhs.get().equals(rhs.get());
    } else {
        return false;
    }

};

boolean __da646e21(Event event, IterativeCondition.Context<Event> context) {
        Optional<String> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        Optional<String> rhs;
try {

var temp = event;
    rhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    rhs = Optional.ofNullable(null);
}


            if (lhs.isPresent() && rhs.isPresent()) {
        return !lhs.get().equals(rhs.get());
    } else {
        return false;
    }

};

boolean __0de065f3(Event event, IterativeCondition.Context<Event> context) {
        Optional<String> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        Optional<String> rhs;
try {

var temp = event;
    rhs = Optional.of(temp.Content.get("x").asText());
} catch (Exception e) {
    rhs = Optional.ofNullable(null);
}


            if (lhs.isPresent() && rhs.isPresent()) {
        return lhs.get().equals(rhs.get());
    } else {
        return false;
    }

};

boolean __6f6dd694(Event event, IterativeCondition.Context<Event> context) {
        Optional<Double> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("price").asDouble());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        var rhs = Optional.of(42.0);
            if (lhs.isPresent() && rhs.isPresent()) {
        return lhs.get() > rhs.get();
    } else {
        return false;
    }

};

boolean __db69d7bf(Event event, IterativeCondition.Context<Event> context) {
        Optional<Boolean> lhs;
try {

    var temp = context.getEventsForPattern("[a]")
            .iterator()
            .next();
    lhs = Optional.of(temp.Content.get("hasFlag").asBoolean());
} catch (Exception e) {
    lhs = Optional.ofNullable(null);
}


        Optional<Boolean> rhs;
try {

var temp = event;
    rhs = Optional.of(temp.Content.get("hasFlag").asBoolean());
} catch (Exception e) {
    rhs = Optional.ofNullable(null);
}


            if (lhs.isPresent() && rhs.isPresent()) {
        return lhs.get() != rhs.get();
    } else {
        return false;
    }

};


}
