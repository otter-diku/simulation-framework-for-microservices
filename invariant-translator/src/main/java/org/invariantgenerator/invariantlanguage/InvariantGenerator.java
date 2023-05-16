package org.invariantgenerator.invariantlanguage;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class InvariantGenerator {

    public static final String GENERATED_INVARIANT_NAMESPACE = "org.invariants";

    private final InvariantTranslator invariantTranslator;

    public InvariantGenerator() {
        invariantTranslator = new InvariantTranslator();
    }

    public void generateInvariantFile(String outputDir, String invariantName, String invariantQuery, Map<String, Object> queueConfig) {
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
        var dataStreamCode = generateDataStreamCode(invariantName, queueConfig, translationResult.id2Type, translationResult.topics, translationResult.schemata);

        var subs = createInvariantFileSubstitutions(invariantName, dataStreamCode, patternCode, patternProcessCode);
        createInvariantFile(outputDir, invariantName, subs);

        // generate pom.xml
    }

    private String generateDataStreamCode(String invariantName, Map<String, Object> queueConfig, Map<String, String> id2Type, Set<String> topics, Map<String, Map<String, String>> schemata) {
        var filterCode = generateFilterCode(new ArrayList<>(id2Type.values()));
        var streamCode = generateStreamCode(invariantName, new ArrayList<>(topics), queueConfig, schemata);
        return String.format(
                """
                private static final String broker = "%s";
                private static final String username = "%s";
                private static final String password = "%s";
                public static DataStream<Event> getDataStream(StreamExecutionEnvironment env) {
                  %s
                  %s
                }
                """, queueConfig.get("broker"), queueConfig.get("username"), queueConfig.get("password"),
                streamCode, filterCode);
    }

    private String generateStreamCode(String invariantName, List<String> topics, Map<String, Object> queueConfig, Map<String, Map<String, String>> schemata) {
        var streamSb = new StringBuilder();
        var unionSb = new StringBuilder();

        for (int i = 0; i < topics.size(); i++) {
            streamSb.append(String.format(
                    """
                    var kafkaSource%s = getEventKafkaSourceAuthenticated(broker, "%s", "%s", username, password);
                    var stream%s = env.fromSource(kafkaSource%s, WatermarkStrategy.noWatermarks(), "Kafka Source");
                    """, i, topics.get(i), invariantName, i, i)
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

        // TODO: currently assuming all events have same timestamp member,
        //       therefore we just pick a random schema
        var schema = new ArrayList<>(schemata.values()).get(0);
        var timestampMember = schema.entrySet().stream()
                .filter(k -> k.getValue().equals("timestamp"))
                .collect(Collectors.toList()).get(0).getKey();

        //          var timestamp = event.Content.get("CreationDate");
        //         return event.Content.get("CreationDate").asLong();
        //     })


        streamSb.append(String.format(
                """
                var streamWithWatermark = stream.assignTimestampsAndWatermarks(
                     WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(%s))
                     .withIdleness(Duration.ofSeconds(3))
                     .withTimestampAssigner((event, l) -> {
                             var timestamp = event.Content.get("%s");
                             if (timestamp.isTextual()) {
                                 return Instant.parse(timestamp.asText()).toEpochMilli();
                             }
                             if (timestamp.isNumber()) {
                                 return timestamp.asLong();
                             }
                             throw new RuntimeException("Failed to parse timestamp of event.");
                         })
                );
                """, maxLateness, timestampMember));

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
                """
        );
        substitutions.put(
                "// ${main}",
                """
                public static void main(String[] args) throws Exception {
                    final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);
                    checkInvariant(env, getDataStream(env));
                }
                """
        );
        substitutions.put(
                "// ${kafkaSink}",
                """
                KafkaSink<String> kafkaSink = createKafkaSink(broker, username, password);
                matches.getSideOutput(outputTag).sinkTo(kafkaSink);
                matches.sinkTo(kafkaSink);
                """
        );
        substitutions.put(
                "// ${MyPatternProcessFunction}",
                patternProcessCode
        );
        return substitutions;
    }

    private void createInvariantFile(String outputDir, String invariantName,
                                     Map<String, String> substitutions) {
        String templateFilePath =
                "src/main/java/org/invariantgenerator/invariantlanguage/InvariantTemplate.java";
        // TODO: instead of this hack use Path and not String
        outputDir = outputDir.endsWith("/") ? outputDir : outputDir + "/";
        // need this for maven
        outputDir = outputDir + "src/main/java/org/invariants/";

        createDirectoryIfNeeded(outputDir);
        var invariantFile = String.format(outputDir + "%s.java", invariantName);

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


}
