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
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, b)
                WITHIN 1 sec
                WHERE (a.id = b.id)
                ON FULL MATCH false""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());

        var InvariantPattern = Pattern.<Event>begin("a")
                .where(SimpleCondition.of(e -> e.Type.equals("A")))
                .followedByAny("b")
                .where(SimpleCondition.of(e -> e.Type.equals("B")))
                .oneOrMore().greedy()
                .where(new IterativeCondition<Event>() {
                    @Override
                    public boolean filter(Event event, Context<Event> context) throws Exception {
                        var a = context.getEventsForPattern("a").iterator().next();
                        var b = event;
                        return a.Content.get("id").equals(b.Content.get("id"));
                    }
                });

    }

    @Test
    public void TestPatternGeneration_() {
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, b)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (a.price > 42)
                ON FULL MATCH false""";
        var translator = new InvariantTranslator();
        var terms = translator.getTermsFromQuery(query);

        Map<String, String> id2Type = new HashMap<>();
        id2Type.put("a", "A");
        id2Type.put("b", "B");

        EventSequence seq = new EventSequence();
        seq.addNode(new SequenceNode(
                false,
                SequenceNodeQuantifier.ONCE,
                Stream.of("a").collect(Collectors.toList())
                )
        );
        seq.addNode(new SequenceNode(
                        false,
                        SequenceNodeQuantifier.ONCE,
                        Stream.of("b").collect(Collectors.toList())
                )
        );

        var patternGenerator = new PatternGenerator();
        patternGenerator.setEventSequence(seq);
        patternGenerator.setTerms(terms);
        patternGenerator.setId2Type(id2Type);
        var pattern = patternGenerator.generatePattern();
    }
}
