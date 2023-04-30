import org.junit.Test;
import org.myorg.flinkinvariants.invariantlanguage.InvariantTranslator;

import static org.junit.jupiter.api.Assertions.*;

public class InvariantNegativeParseTests {

    @Test(expected = Exception.class)
    public void TestSchemaTopicMissing() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  schema: {id}
                               
                EVENT SEQ (a, b)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "").getNumberOfSyntaxErrors();
        assertEquals(0, numParseErrors);
    }

    @Test(expected = Exception.class)
    public void TestEventSEQMissing() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "").getNumberOfSyntaxErrors();
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestInvariantMissing() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, b)""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "").getNumberOfSyntaxErrors();
        assertEquals(1, numParseErrors);
    }

    @Test
    public void TermReferencesMoreThan1NegatedEvent() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, !b, !c)
                WITHIN 1 sec
                WHERE (b.x = a.x OR c.x = a.x)
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, result.getNumberOfSyntaxErrors());
        assertTrue(result.isSemanticAnalysisFailed());
    }

    @Test
    public void EventIdReferencedMultipleTimes() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, !b, a)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query,
                "TestInvariant",
                "");

        assertEquals(0, result.getNumberOfSyntaxErrors());
        assertTrue(result.isSemanticAnalysisFailed());
    }

    @Test
    public void TermReferencesNegatedEventAndSubsequentEvents() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (b.x = a.x OR b.y = c.y)
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query,
                "TestInvariant",
                "");

        assertEquals(0, result.getNumberOfSyntaxErrors());
        assertTrue(result.isSemanticAnalysisFailed());
    }
}
