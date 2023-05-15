package org.invariantgenerator.invariantlanguage;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class InvariantGenerator {

    public static final String GENERATED_INVARIANT_NAMESPACE = "org.invariantgenerator.invariantlanguage.generated";

    private final InvariantTranslator invariantTranslator;

    public InvariantGenerator() {
        invariantTranslator = new InvariantTranslator();
    }

    public void generateInvariantFile(String invariantName, String invariantQuery, Map<String, Object> queueConfig) {
        var translationResult = invariantTranslator.translateQuery(invariantQuery);

        var patternGenerator = new PatternGenerator(
                translationResult.sequence,
                translationResult.whereClauseTerms,
                translationResult.id2Type,
                translationResult.schemata,
                translationResult.within,
                translationResult.onFullMatch,
                translationResult.onPartialMatch);

        var patternCode = patternGenerator.generatePattern();
        var patternProcessCode = patternGenerator.generatePatternProcessFunction();
        var dataStreamCode = generateDataStreamCode(queueConfig, translationResult.id2Type, translationResult.topics);

        var subs = createInvariantFileSubstitutions(invariantName, dataStreamCode, patternCode, patternProcessCode);
        createInvariantFile(invariantName, subs);

        // generate pom.xml
    }

    private String generateDataStreamCode(Map<String, Object> queueConfig, Map<String, String> id2Type, Set<String> topics) {
        var filterCode = generateFilterCode(new ArrayList<>(id2Type.values()));
        var streamCode = generateStreamCode(new ArrayList<>(topics), queueConfig);
        return String.format(
                """
                public static DataStream<Event> getDataStream(StreamExecutionEnvironment env) {
                  %s
                  %s
                }
                """, streamCode, filterCode);
    }

    private String generateStreamCode(List<String> topics, Map<String, Object> queueConfig) {
        var streamSb = new StringBuilder();
        var unionSb = new StringBuilder();
        var broker = (String) queueConfig.get("broker");

        for (int i = 0; i < topics.size(); i++) {
            streamSb.append(String.format(
                    """
                    var kafkaSource%s = KafkaReader.getEventKafkaSourceAuthenticated("%s", "%s", "%s", "%s");
                    var stream%s = env.fromSource(kafkaSource%s, WatermarkStrategy.noWatermarks(), "Kafka Source");
                    """, i, broker, topics.get(i), queueConfig.get("username"), queueConfig.get("password"), i, i)
            );
            if (i == topics.size()-1) {
                unionSb.append("stream").append(i);
            } else {
                unionSb.append("stream").append(i).append(", ");
            }
        }

        if (topics.size() > 1) {
            streamSb.append(String.format("var stream = stream0.union(%s);\n", unionSb));
        } else {
            streamSb.append("var stream = stream0;\n");
        }

        var maxLateness = queueConfig.get("maxLatenessOfEventsSec");
        streamSb.append(String.format(
                """
                var streamWithWatermark = stream.assignTimestampsAndWatermarks(
                                WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(%s)));
                """, maxLateness));

        return streamSb.toString();
    }

    private String generateFilterCode(List<String> types) {
        var filterConditions = new StringBuilder();

        for (var type : types.subList(0, types.size() - 1)) {
            filterConditions.append(String.format("e.Type.equals(\"%s\") ||\n", type));
        }
        filterConditions.append(String.format("e.Type.equals(\"%s\")", types.get(types.size()-1)));

        return String.format(
                """
                var filteredStream = streamWithWatermark.filter(e ->
                    %s
                ).setParallelism(1);
                return filteredStream;
                """, filterConditions);
    }


    private Map<String, String> createInvariantFileSubstitutions(
            String invariantName, String dataStreamCode,
            String patternCode, String patternProcessCode)  {
        Map<String, String> substitutions = new HashMap<>();
        substitutions.put(
                "// ${DataStreamCode}",
                dataStreamCode
        );
        substitutions.put(
                "public static Pattern<Event, ?> invariant;",
                "public static Pattern<Event, ?> invariant = \n" + patternCode
        );
        substitutions.put(
                "public class InvariantTemplate {",
                String.format("public class %s {",
                        invariantName)
        );
        substitutions.put(
                "package org.invariantgenerator.invariantlanguage;",
                "package " + GENERATED_INVARIANT_NAMESPACE + ";"
        );

        substitutions.put(
                ";// ${process}",
                """
                .process(new MyPatternProcessFunction());
                matches.getSideOutput(outputTag).addSink(sinkFunction);
                matches.addSink(sinkFunction);
                """
        );
        substitutions.put(
                "// ${main}",
                """
                public static void main(String[] args) throws Exception {
                    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);
                    checkInvariant(env, getDataStream(env), new PrintSinkFunction<>());
                }
                """
        );
        substitutions.put(
                "// ${MyPatternProcessFunction}",
                patternProcessCode
        );
        return substitutions;
    }

    private void createInvariantFile(String invariantName,
                                     Map<String, String> substitutions) {
        String templateFilePath =
                "src/main/java/org/invariantgenerator/invariantlanguage/InvariantTemplate.java";
        var destDir = "src/main/java/org/invariantgenerator/invariantlanguage/generated/";
        createDirectoryIfNeeded(destDir);
        var invariantFile = String.format(destDir + "%s.java", invariantName);

        try {
            FileWriter fileWriter = getFileWriter(invariantFile);
            BufferedWriter writer = new BufferedWriter(fileWriter);
            BufferedReader reader = new BufferedReader(new FileReader(templateFilePath));
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

    private static FileWriter getFileWriter(String invariantFile) throws IOException {
        File file = new File (invariantFile);
        if (!file.exists()) {
            file.createNewFile();
        }

        return new FileWriter(file);
    }

    private List<String> generatePomConfiguration(List<String> invariantNames) {
        // each invariant needs to have separate
        // entry in pom.xml such that its jar gets generated on `mvn package`
        var temp = String.format(
                """
                <execution>
                    <id>%s</id>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <outputFile>target/%s.jar</outputFile>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                        <artifactSet>
                            <excludes>
                                <exclude>org.apache.flink:flink-shaded-force-shading</exclude>
                                <exclude>com.google.code.findbugs:jsr305</exclude>
                                <exclude>org.slf4j:*</exclude>
                                <exclude>org.apache.logging.log4j:*</exclude>
                            </excludes>
                        </artifactSet>
                        <filters>
                            <filter>
                                <!-- Do not copy the signatures in the META-INF folder.
                                Otherwise, this might cause SecurityExceptions when using the JAR. -->
                                <artifact>*:*</artifact>
                                <excludes>
                                    <exclude>META-INF/*.SF</exclude>
                                    <exclude>META-INF/*.DSA</exclude>
                                    <exclude>META-INF/*.RSA</exclude>
                                </excludes>
                            </filter>
                        </filters>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>%s</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
                """, "invariantName", "invariantName", "mainClass"
        );

        return List.of();
    }
}
