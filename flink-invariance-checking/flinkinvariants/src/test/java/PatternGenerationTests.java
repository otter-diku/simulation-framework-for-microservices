import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.junit.Test;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.flinkinvariants.invariantlanguage.*;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PatternGenerationTests {

    @Test
    public void TestPatternGeneration() {
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id:number}
                B b
                  topic: b-topic
                  schema: {id:number}
                C c
                  topic: c-topic
                  schema: {id:number}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (a.price > 42)
                ON FULL MATCH false""";
        var translator = new InvariantTranslator();
        var terms = translator.getTermsFromQuery(query);

        Map<String, String> id2Type = new HashMap<>();
        id2Type.put("a", "A");
        id2Type.put("b", "B");
        id2Type.put("c", "C");


        EventSequence seq = new EventSequence();
        seq.addNode(new SequenceNode(
                false,
                SequenceNodeQuantifier.ONCE,
                Stream.of("a").collect(Collectors.toList())
                )
        );
        seq.addNode(new SequenceNode(
                        true,
                        SequenceNodeQuantifier.ONCE,
                        Stream.of("b").collect(Collectors.toList())
                )
        );
        seq.addNode(new SequenceNode(
                        false,
                        SequenceNodeQuantifier.ONCE,
                        Stream.of("c").collect(Collectors.toList())
                )
        );


        var schemata = new HashMap<String, List<Tuple2<String, String>>>();

        schemata.put("a", List.of(new Tuple2<>("id", "number")));
        schemata.put("b", List.of(new Tuple2<>("id", "number")));
        schemata.put("c", List.of(new Tuple2<>("id", "number")));

        var patternGenerator = new PatternGenerator(seq, terms, id2Type, schemata);
        var pattern = patternGenerator.generatePattern();
    }
}
