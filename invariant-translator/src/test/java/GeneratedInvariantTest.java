import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Test;
import org.invarianttranslator.events.Event;
import org.shared.InvariantChecker;
import org.invariantgenerator.invariantlanguage.InvariantTranslator;
import org.invariantgenerator.invariantlanguage.PatternGenerator;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedInvariantTest {

    public static final String GENERATED_INVARIANT_NAMESPACE = "org.invariantgenerator.invariantlanguage.generated";
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

    @Test
    public void testGeneratedInvariant_7() throws Exception {
        var invariantQuery =
                """
                IQR iqr
                  topic: insurance-quote-requests
                  schema: {id:number, customerAge:number, insuranceType:string}

                SEQ (iqr)
                WHERE (iqr.insuranceType = 'life-insurance')
                ON FULL MATCH (iqr.customerAge < 99 AND iqr.customerAge > 1)""";
        var events = List.of(
                new Event("IQR", """
                {"id": 1, "customerAge": 0, "insuranceType": "life-insurance"}"""),
                new Event("IQR", """
                {"id": 2, "customerAge": 102, "insuranceType": "life-insurance"}"""),
                new Event("A", """
                {"id": 2}"""),
                new Event("IQR", """
                {"id": 2, "customerAge": 102, "insuranceType": "car-insurance"}"""),
                new Event("IQR", """
                {"id": 2, "customerAge": 23, "insuranceType": "life-insurance"}"""),
                new Event("A", """
                {"id": 2}"""),
                new Event("B", """
                {"id": 3}""")
        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_7");

        assertEquals(2, ViolationSink.values.size());
        assertTrue(ViolationSink.values.stream()
                .anyMatch(s -> s.contains("{\"id\":2,\"customerAge\":102,\"insuranceType\":\"life-insurance\"}")));
        assertTrue(ViolationSink.values.stream()
                .anyMatch(s -> s.contains("\"customerAge\":0,\"insuranceType\":\"life-insurance\"}")));
    }

    @Test
    public void testGeneratedInvariant_8() throws Exception {
        var invariantQuery =
                """
                IQR iqr
                  topic: insurance-quote-requests
                  schema: {id:number, customerAddress:string, insuranceType:string}

                SEQ (iqr)
                WHERE (iqr.insuranceType = 'car-insurance')
                ON FULL MATCH (iqr.customerAddress != 'Switzerland')""";
        var events = List.of(
                new Event("IQR", """
                {"id": 1, "customerAddress": "Switzerland", "insuranceType": "car-insurance"}"""),
                new Event("IQR", """
                {"id": 2, "customerAddress": "China", "insuranceType": "car-insurance"}"""),
                new Event("A", """
                {"id": 2}"""),
                new Event("IQR", """
                {"id": 2, "customerAddress": "Germany", "insuranceType": "car-insurance"}"""),
                new Event("IQR", """
                {"id": 2, "customerAddress": "Poland", "insuranceType": "car-insurance"}"""),
                new Event("B", """
                {"id": 3}""")
        );
        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_8");

        assertEquals(1, ViolationSink.values.size());
        assertTrue(ViolationSink.values.stream()
                .anyMatch(s -> s.contains("Switzerland")));
    }

    @Test
    public void testGeneratedInvariant_9() throws Exception {
        // NOTE: awkward within timing due to processing time, test might fail
        var invariantQuery =
                """
                OrderSubmitted os
                  topic: eshop_event_bus
                  schema: {orderId:number}
                PaymentSucceeded ps
                  topic: eshop_event_bus
                  schema: {orderId:number}
                PaymentFailed pf
                  topic: eshop_event_bus
                  schema: {orderId:number}

                SEQ (os, (ps | pf))
                WITHIN 30 msec
                WHERE (os.orderId = ps.orderId OR
                      os.orderId = pf.orderId)
                ON PREFIX MATCH DEFAULT false""";
        var events = List.of(
                new Event("OrderSubmitted", """
                {"orderId": 3}"""),
                new Event("OrderSubmitted", """
                {"orderId": 1}"""),
                new Event("OrderSubmitted", """
                {"orderId": 2}"""),
                new Event("PaymentSucceeded", """
                {"orderId": 1}"""),
                new Event("PaymentFailed", """
                {"orderId": 2}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 42, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 43, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 44, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 45, "total_cost": 1}""")

        );

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_9");

        assertEquals(1, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_10() throws Exception {
        var invariantQuery =
                """
                OrderSubmitted os
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentSucceeded ps
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentFailed pf
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}

                SEQ (os, (ps | pf))
                WITHIN 1 msec
                WHERE (os.orderId = ps.orderId OR
                      os.orderId = pf.orderId)
                ON PREFIX MATCH DEFAULT (os.total_cost > 1)
                """;
        var events = List.of(
                new Event("OrderSubmitted", """
                        {"orderId": 2, "total_cost": -12}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 1, "total_cost": 337}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 4, "total_cost": 129 }"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 5, "total_cost": 337}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 6, "total_cost": -12}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 7, "total_cost": 337}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 8, "total_cost": -12}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 9, "total_cost": 337}""")
        );
        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_10");
        assertEquals(1, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_11() throws Exception {
        // NOTE: this tests if we get correct number of violations from FULL match
        //       and PREFIX match, but since we are using processing time
        //       the within time limit is a bit awkward, and could event have different results
        //       on different machines
        var invariantQuery =
                """
                OrderSubmitted os
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentSucceeded ps
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentFailed pf
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}

                SEQ (os, (ps | pf))
                WITHIN 20 msec
                WHERE (os.orderId = ps.orderId OR
                      os.orderId = pf.orderId)
                ON FULL MATCH (os.total_cost = ps.total_cost OR os.total_cost = pf.total_cost)
                ON PREFIX MATCH DEFAULT (os.total_cost > 1)
                """;
        var events = List.of(
                new Event("OrderSubmitted", """
                        {"orderId": 2, "total_cost": -12}"""),
                new Event("OrderSubmitted", """
                        {"orderId": 1, "total_cost": 335}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 1, "total_cost": 336}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 42, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 43, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 44, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 45, "total_cost": 1}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 46, "total_cost": 1}""")
                );
        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_11");
        assertEquals(2, ViolationSink.values.size());
    }

    @Test
    public void testGeneratedInvariant_12() throws Exception {
        // test negative number atom
        var invariantQuery =
                """
                OrderSubmitted os
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentSucceeded ps
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}
                PaymentFailed pf
                  topic: eshop_event_bus
                  schema: {orderId:number, total_cost:number}

                SEQ (os, ps, pf)
                WITHIN 1 msec
                WHERE (os.orderId = ps.orderId OR
                      os.orderId = pf.orderId)
                ON PREFIX MATCH DEFAULT (os.total_cost > -10)
                """;
        var events = List.of(
                new Event("OrderSubmitted", """
                        {"orderId": 1, "total_cost": 337}"""),
                new Event("OrderSubmitted", """
                        {"orderId": 2, "total_cost": -12}"""),
                new Event("PaymentSucceeded", """
                        {"orderId": 3, "total_cost": 1}""")
        );
        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_12");
        assertEquals(1, ViolationSink.values.size());
    }


    @Test
    public void testGeneratedInvariant_13() throws Exception {
        // NOTE: awkward within timing due to processing time, test might fail
        var invariantQuery =
                """
                A a
                  topic: eshop_event_bus
                  schema: {id:number, x:number}
                B b
                  topic: eshop_event_bus
                  schema: {id:number, x:number}
                C c
                  topic: eshop_event_bus
                  schema: {id:number, x:number}
                D d
                  topic: eshop_event_bus
                  schema: {id:number, x:number}
                E e
                  topic: eshop_event_bus
                  schema: {id:number, x:number}
                F f
                  topic: eshop_event_bus
                  schema: {id:number, x:number}

                SEQ (a, !b, (c|d), e, f)
                WITHIN 5 msec
                WHERE (a.id = b.id) AND (c.id = a.id OR d.id = a.id) AND (e.id = a.id)
                ON PREFIX MATCH (a) (a.id < 0)
                ON PREFIX MATCH (a, (c|d)) (c.x > 5 OR d.x > 5)
                ON PREFIX MATCH DEFAULT (a.id = 1)
                """;

        var events = new ArrayList<>(List.of(
                new Event("A", """
                        {"id": 1, "x": 1}"""),
                new Event("B", """
                        {"id": 2, "x": 2}"""),
                new Event("D", """
                        {"id": 1, "x": 3}""")
        ));

        for (int x = 0; x < 1000; x++) {
            events.add(new Event("X", "\"id\": 0"));
        }

        executeTestInvariant(invariantQuery, events, "testGeneratedInvariant_13");

        assertEquals(1, ViolationSink.values.size());
    }


    private void debugTestInvariant(InvariantChecker invariantChecker, List<Event> events) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var stream = env.fromElements(events.toArray(new Event[0]));
        invariantChecker.checkInvariant(env, stream, new ViolationSink());
    }


    private Optional<InvariantChecker> translateTestInvariant(String invariantQuery, String invariantName) throws Exception {
        var translator = new InvariantTranslator();
        var translationResult = translator.translateQuery(invariantQuery);

        var patternGenerator = new PatternGenerator(
                translationResult.sequence,
                translationResult.whereClauseTerms,
                translationResult.id2Type,
                translationResult.schemata,
                translationResult.within,
                translationResult.onFullMatch,
                translationResult.onPartialMatch);
        var pattern = patternGenerator.generatePattern();
        var processMatchCode = patternGenerator.generatePatternProcessFunction();

        createTestInvariantFile(pattern, processMatchCode, invariantName);

        // Compile source file.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        var filePath = String.format("src/main/java/org/invariantgenerator/invariantlanguage/generated/%s.java", invariantName);

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
            Class<?> loadedClass = classLoader.loadClass(GENERATED_INVARIANT_NAMESPACE + "." + invariantName);
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
                "src/main/java/org/invariantgenerator/invariantlanguage/TestInvariantTemplate.java";

        var destDir = "src/main/java/org/invariantgenerator/invariantlanguage/generated/";

        createDirectoryIfNeeded(destDir);

        var invariantFile = String.format(destDir + "%s.java"
                , invariantName);

        Map<String, String> substitutions = new HashMap<>();
        substitutions.put("public Pattern<Event, ?> invariant;",
                "public Pattern<Event, ?> invariant = \n" + pattern);
        substitutions.put(
                "public class TestInvariantTemplate implements InvariantChecker {",
                String.format("public class %s implements InvariantChecker {", invariantName)
        );
        substitutions.put("package org.invariantgenerator.invariantlanguage;",
                "package " + GENERATED_INVARIANT_NAMESPACE + ";");

        substitutions.put(";// ${process}", """
                .process(new MyPatternProcessFunction());
                matches.getSideOutput(outputTag).addSink(sinkFunction);
                matches.addSink(sinkFunction);
                """);
        substitutions.put("// ${MyPatternProcessFunction}", processMatchCode);

        try {
            FileWriter fileWriter = getFileWriter(invariantFile);
            BufferedWriter writer = new BufferedWriter(fileWriter);

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));


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

    private static void createDirectoryIfNeeded(String directory) {
        File file = new File (directory);
        if (!file.exists())
        {
            file.mkdirs();
        }
    }

    @NotNull
    private static FileWriter getFileWriter(String invariantFile) throws IOException {
        File file = new File (invariantFile);
        if (!file.exists()) {
            file.createNewFile();
        }

        return new FileWriter(file);
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
