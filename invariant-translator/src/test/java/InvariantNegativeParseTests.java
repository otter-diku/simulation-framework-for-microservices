import org.junit.Test;
import org.invariantgenerator.invariantlanguage.InvariantTranslator;

import static org.junit.jupiter.api.Assertions.*;

public class InvariantNegativeParseTests {

    @Test(expected = Exception.class)
    public void TestSchemaTopicMissing() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id:number}
                B b
                  schema: {id:number}
                               
                EVENT SEQ (a, b)
                INVARIANT a.id = 42""";

        var _discard = translator.translateQuery(
                query
        ).getNumberOfSyntaxErrors();
    }

    @Test(expected = Exception.class)
    public void TestEventSEQMissing() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id:number}
                B b
                  topic: b-topic
                  schema: {id:number}
                               
                INVARIANT a.id = 42""";

        var _discard = translator.translateQuery(
                query
        ).getNumberOfSyntaxErrors();
    }

    @Test
    public void TermReferencesMoreThan1NegatedEvent() {
        var translator = new InvariantTranslator();
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
                               
                SEQ (a, !b, !c)
                WITHIN 1 sec
                WHERE (b.x = a.x OR c.x = a.x)
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query
        );
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
                  schema: {id:number}
                B b
                  topic: b-topic
                  schema: {id:number}
                C c
                  topic: c-topic
                  schema: {id:number}
                               
                SEQ (a, !b, a)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query
        );

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
                  schema: {id:number}
                B b
                  topic: b-topic
                  schema: {id:number}
                C c
                  topic: c-topic
                  schema: {id:number}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (b.x = a.x OR b.y = c.y)
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query
        );

        assertEquals(0, result.getNumberOfSyntaxErrors());
        assertTrue(result.isSemanticAnalysisFailed());
    }
}
