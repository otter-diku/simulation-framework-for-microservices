import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.ClassRule;
import org.junit.Test;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.flinkinvariants.invariantlanguage.InvariantChecker;
import org.myorg.flinkinvariants.invariantlanguage.InvariantTranslator;
import org.myorg.flinkinvariants.invariantlanguage.PatternGenerator;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedInvariantTest {

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @Test
    public void testGeneratedInvariant_0() throws Exception {
        var invariantQuery =
                """
                A a
                  topic: a-topic
                  schema: {id:string, price:number, hasFlag:bool}
                B b
                  topic: b-topic
                  schema: {id:string}
                C c
                  topic: c-topic
                  schema: {id:string, hasFlag:bool}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
                ON FULL MATCH false""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 2}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}""")
        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_0");
        assertEquals(1, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_1() throws Exception {
        var invariantQuery =
                """
                A a
                  topic: a-topic
                  schema: {id:string, price:number, hasFlag:bool}
                B b
                  topic: b-topic
                  schema: {id:string}
                C c
                  topic: c-topic
                  schema: {id:string, hasFlag:bool}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
                ON FULL MATCH false""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 1}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}""")
        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_1");
        assertEquals(0, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_2() throws Exception {
        var invariantQuery =
                """
                A a
                  topic: a-topic
                  schema: {id:string, x:string, price:number, hasFlag:bool}
                B b
                  topic: b-topic
                  schema: {id:string, x:string}
                C c
                  topic: c-topic
                  schema: {id:string, hasFlag:bool}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (a.id = b.id OR a.x = b.x AND (a.x != b.x OR (a.x = b.x))) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
                ON FULL MATCH false""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "x": "2", "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 2, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}""")
        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_2");
        assertEquals(1, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_3() throws Exception {
        var invariantQuery =
                """
                A a
                  topic: a-topic
                  schema: {id:string, x:string, price:number, hasFlag:bool}
                B b
                  topic: b-topic
                  schema: {id:string, x:string}
                C c
                  topic: c-topic
                  schema: {id:string, hasFlag:bool}
                D d
                  topic: d-topic
                  schema: {id:string}
                               
                SEQ (a, (b|c)+, d)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (c.id = a.id)
                ON FULL MATCH false""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "x": "2", "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "x": "2"}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}"""),
                new Event("D", """
                {"id": 1}"""));

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_3");
    }


    @Test
    public void testGeneratedInvariant_4() throws Exception {
        var invariantQuery =
                """
                A a
                  topic: a-topic
                  schema: {id:string, x:string, price:number, hasFlag:bool}
                B b
                  topic: b-topic
                  schema: {id:string, x:string}
                C c
                  topic: c-topic
                  schema: {id:string, hasFlag:bool}
                D d
                  topic: d-topic
                  schema: {id:string}
                               
                SEQ (a, (b|c)+, d)
                WITHIN 1 sec
                WHERE (a.id = b.id) AND (c.id = a.id)
                ON FULL MATCH (a.id != d.id) AND (b.id = a.id)""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "x": "2", "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "x": "2"}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}"""),
                new Event("D", """
                {"id": 1}"""));

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_4");
    }


    private void executeTestInvariant(String invariantQuery, List<Event> events, String fileName) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var stream = env.fromElements(events.toArray(new Event[0]));
        var translator = new InvariantTranslator();
        var translationResult = translator.translateQuery(invariantQuery, null, null);

        var patternGenerator = new PatternGenerator(
                translationResult.sequence,
                translationResult.whereClauseTerms,
                translationResult.id2Type,
                translationResult.schemata,
                translationResult.within,
                translationResult.onFullMatch,
                translationResult.onPartialMatch);
        var pattern = patternGenerator.generatePattern();
        var processMatchCode = patternGenerator.generateInvariants();

        runInvariant(env, stream, pattern, fileName);
    }





    private void runInvariant(StreamExecutionEnvironment env, DataStreamSource<Event> stream, String pattern, String invariantName) throws Exception {
        var destDir = "src/main/java/org/myorg/flinkinvariants/invariantlanguage/";
        var invariantFile = String.format(destDir + "%s.java"
                , invariantName);
        Map<String, String> substitution = new HashMap<>();
        substitution.put("public Pattern<Event, ?> invariant;",
                "public Pattern<Event, ?> invariant = \n" + pattern);
        substitution.put(
                "public class TestInvariantTemplate implements InvariantChecker {",
                String.format("public class %s implements InvariantChecker {", invariantName)
        );

        createTestInvariantFile(invariantFile, substitution);

        // Compile source file.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, invariantFile);

        // Load and instantiate compiled class.
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] {});
        Class<?> cls = Class.forName("org.myorg.flinkinvariants.invariantlanguage." + invariantName, true, classLoader);

        InvariantChecker invariantChecker = (InvariantChecker) cls.getDeclaredConstructor().newInstance();


        ViolationSink.values.clear();
        invariantChecker.checkInvariant(env, stream, new ViolationSink());
    }


    private void createTestInvariantFile(String outputFile, Map<String, String> substitions) {
        String inputFile =
                "src/main/java/org/myorg/flinkinvariants/invariantlanguage/TestInvariantTemplate.java";

        try {
            BufferedReader reader = new BufferedReader(new java.io.FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            String line = reader.readLine();
            while (line != null) {
                for (var key : substitions.keySet()) {
                    if (line.contains(key)) {
                        line = substitions.get(key);
                    }
                }
                writer.write(line);
                writer.newLine();
                line = reader.readLine();
            }
            writer.flush();
            reader.close();
            writer.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static class ViolationSink implements SinkFunction<String> {

        // must be static
        public static final List<String> values = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(String value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }

}
