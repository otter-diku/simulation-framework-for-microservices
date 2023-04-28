import org.junit.Test;
import org.myorg.flinkinvariants.invariantlanguage.InvariantTranslator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvariantPositiveParseTests {

    @Test
    public void TestEventSeq1() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                EVENT SEQ (a, b)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq2() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                EVENT SEQ (a, [!b], a)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq2_0() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                EVENT SEQ (a, [!b]*, a)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq3() {
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
                               
                EVENT SEQ (a, b+, c)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq4() {
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
                               
                EVENT SEQ (a, b*, c)
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq5() {
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
                               
                EVENT SEQ (a, (b | c))
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

    @Test
    public void TestEventSeq6() {
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
                               
                EVENT SEQ ((a | c), (b | c), (a | (a | b)))
                INVARIANT a.id = 42""";

        var numParseErrors = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, numParseErrors);
    }

}
