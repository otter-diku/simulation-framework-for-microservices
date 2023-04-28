import org.junit.Test;
import org.myorg.flinkinvariants.invariantlanguage.InvariantTranslator;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "");
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
                "");
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
                               
                EVENT SEQ (a, b)""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(1, numParseErrors);
    }

}
