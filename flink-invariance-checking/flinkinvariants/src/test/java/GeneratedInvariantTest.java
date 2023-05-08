import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.ClassRule;
import org.junit.Test;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.flinkinvariants.invariantlanguage.*;

import javax.tools.*;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;

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
                {"id": 2, "x": "1"}"""),
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
                {"id": 1, "hasFlag": true}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 1, "hasFlag": false}"""),
                new Event("D", """
                {"id": 1}"""));

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_3");
        // expecting only 1 match
        assertEquals(1, ViolationSink.values.size());

        // all B and C events should be present
        var patternB = java.util.regex.Pattern.compile("Type='B'");
        var patternC = java.util.regex.Pattern.compile("Type='C'");
        Matcher matcherB = patternB.matcher(ViolationSink.values.get(0));
        Matcher matcherC = patternC.matcher(ViolationSink.values.get(0));
        assertEquals(2, matcherB.results().count());
        assertEquals(2, matcherC.results().count());
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
                               
                SEQ (a, b, !c, d)
                WITHIN 1 sec
                WHERE (c.id = a.id)
                ON FULL MATCH (a.id != d.id)""";
        var events = List.of(
                new Event("A", """
                {"id": 1, "x": "2", "price": 52, "hasFlag": true}"""),
                new Event("B", """
                {"id": 1, "x": "2"}"""),
                new Event("C", """
                {"id": 2, "hasFlag": true}"""),
                new Event("D", """
                {"id": 1}"""));

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_4");

        assertEquals(1, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_5() throws Exception {
        // product price changed
        var invariantQuery =
                """
                PC pc1
                  topic: eshop_event_bus
                  schema: {ProductId:number, NewPrice:number}
                PC pc2
                  topic: eshop_event_bus
                  schema: {ProductId:number, NewPrice:number}
                PB pb
                  topic: eshop_event_bus
                  schema: {ProductId:number, Price:number}
                
                SEQ (pc1, !pc2, pb)
                WITHIN 2 min
                WHERE (pc1.ProductId = pb.ProductId) AND
                      (pc1.ProductId = pc2.ProductId)
                ON FULL MATCH (pc1.NewPrice = pb.Price)""";
        var events = List.of(
                new Event("PC", """
                {"ProductId": 1, "NewPrice": "1"}"""),
                new Event("PC", """
                {"ProductId": 2, "NewPrice": "2"}"""),
                new Event("PC", """
                {"ProductId": 2, "NewPrice": "42"}"""),
                new Event("PB", """
                {"ProductId": 2, "Price": 42}"""),
                new Event("PB", """
                {"ProductId": 1, "Price": 1}""")
                );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_5");

        assertEquals(0, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_6() throws Exception {
        // product price changed
        var invariantQuery =
                """
                PC pc1
                  topic: eshop_event_bus
                  schema: {ProductId:number, NewPrice:number}
                PC pc2
                  topic: eshop_event_bus
                  schema: {ProductId:number, NewPrice:number}
                PB pb
                  topic: eshop_event_bus
                  schema: {ProductId:number, Price:number}
                
                SEQ (pc1, !pc2, pb)
                WITHIN 2 min
                WHERE (pc1.ProductId = pb.ProductId) AND
                      (pc1.ProductId = pc2.ProductId)
                ON FULL MATCH (pc1.NewPrice = pb.Price)""";
        var events = List.of(
                new Event("PC", """
                {"ProductId": 1, "NewPrice": "1"}"""),
                new Event("PC", """
                {"ProductId": 2, "NewPrice": "2"}"""),
                new Event("PC", """
                {"ProductId": 2, "NewPrice": "42"}"""),
                new Event("PB", """
                {"ProductId": 2, "Price": 43}"""),
                new Event("PB", """
                {"ProductId": 1, "Price": 11}""")
        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_6");

        assertEquals(2, ViolationSink.values.size());
    }

    private void debugTestInvariant(InvariantChecker invariantChecker, List<Event> events) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var stream = env.fromElements(events.toArray(new Event[0]));
        invariantChecker.checkInvariant(env, stream, new ViolationSink());
    }


    private Optional<InvariantChecker> translateTestInvariant(String invariantQuery, String invariantName) throws Exception {
        var translator = new InvariantTranslator();
        var translationResult = translator.translateQuery(invariantQuery, null, null);

        var patternGenerator = new PatternGenerator(
                translationResult.sequence,
                translationResult.whereClauseTerms,
                translationResult.fullMatchTerms,
                translationResult.id2Type,
                translationResult.schemata,
                translationResult.within,
                translationResult.onFullMatch,
                translationResult.onPartialMatch);
        var pattern = patternGenerator.generatePattern();
        var processMatchCode = patternGenerator.generateInvariants();

        createTestInvariantFile(pattern, processMatchCode, invariantName);

        // Compile source file.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        var filePath = String.format("src/main/java/org/myorg/flinkinvariants/invariantlanguage/%s.java", invariantName);

        Iterable<? extends JavaFileObject> compilationUnit
                = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(new File(filePath)));
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                null,
                null,
                null,
                null,
                compilationUnit);
        if (task.call()) {
            // Create a new custom class loader, pointing to the directory that contains the compiled
            // classes, this should point to the top of the package structure!
            URLClassLoader classLoader = new URLClassLoader(new URL[]{new File("src/main/java").toURI().toURL()});

            // Load the class from the classloader by name....
            Class<?> loadedClass = classLoader.loadClass("org.myorg.flinkinvariants.invariantlanguage." + invariantName);
            // Create a new instance...
            InvariantChecker invariantChecker = (InvariantChecker) loadedClass.getDeclaredConstructor().newInstance();
            return Optional.of(invariantChecker);
        }
        return Optional.empty();
    }


    private void runTestInvariant(InvariantChecker invariantChecker, List<Event> events) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var stream = env.fromElements(events.toArray(new Event[0]));

        ViolationSink.values.clear();
        invariantChecker.checkInvariant(env, stream, new ViolationSink());
    }

    private void executeTestInvariant(String invariantQuery, List<Event> events, String invariantName) throws Exception {
        var invariantChecker = translateTestInvariant(invariantQuery, invariantName).get();
        runTestInvariant(invariantChecker, events);
    }


    private void createTestInvariantFile(String pattern, String processMatchCode, String invariantName) {
        String inputFile =
                "src/main/java/org/myorg/flinkinvariants/invariantlanguage/TestInvariantTemplate.java";

        var destDir = "src/main/java/org/myorg/flinkinvariants/invariantlanguage/";
        var invariantFile = String.format(destDir + "%s.java"
                , invariantName);

        Map<String, String> substitutions = new HashMap<>();
        substitutions.put("public Pattern<Event, ?> invariant;",
                "public Pattern<Event, ?> invariant = \n" + pattern);
        substitutions.put(
                "public class TestInvariantTemplate implements InvariantChecker {",
                String.format("public class %s implements InvariantChecker {", invariantName)
        );
        substitutions.put(";// ${process}", ".process(new MyPatternProcessFunction())\n.addSink(sinkFunction);");
        substitutions.put("// ${MyPatternProcessFunction}", processMatchCode);

        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(invariantFile));
            String line = reader.readLine();
            while (line != null) {
                for (var key : substitutions.keySet()) {
                    if (line.contains(key)) {
                        line = substitutions.get(key);
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
