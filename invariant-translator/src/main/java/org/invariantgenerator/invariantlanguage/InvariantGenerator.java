package org.invariantgenerator.invariantlanguage;

import java.io.*;
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

        var subs = createInvariantFileSubstitutions(invariantName, dataStreamCode, patternCode, patternProcessCode, queueConfig);
        // createInvariantFile(outputDir, invariantName, subs);
        createInvariantFile2(outputDir, invariantName, subs);

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
            if (i == topics.size() - 1) {
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
        filterConditions.append(String.format("e.Type.equals(\"%s\")", types.get(types.size() - 1)));

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
            String patternCode, String patternProcessCode, Map<String, Object> queueConfig) {
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
        var violationTopic = queueConfig.getOrDefault("violationsTopicName", "violations");
        substitutions.put(
                "// ${kafkaSink}",
                String.format(
                        """
                                KafkaSink<String> kafkaSink = createKafkaSink(broker, "%s", username, password);
                                matches.getSideOutput(outputTag).sinkTo(kafkaSink);
                                matches.sinkTo(kafkaSink);
                                """, violationTopic)
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

    private void createInvariantFile2(String outputDir, String invariantName,
                                     Map<String, String> substitutions) {
        String templateFilePath =
                "src/main/java/org/invariantgenerator/invariantlanguage/InvariantTemplate.java";
        // TODO: instead of this hack use Path and not String
        outputDir = outputDir.endsWith("/") ? outputDir : outputDir + "/";
        // need this for standard maven project structure
        outputDir = outputDir + "src/main/java/org/invariants/";

        createDirectoryIfNeeded(outputDir);
        var invariantFile = String.format(outputDir + "%s.java", invariantName);

        var invariantContent = invariantTemplate;
        for (var key : substitutions.keySet()) {
             invariantContent = invariantContent.replace(key, substitutions.get(key));
        }
        try {
            FileWriter fileWriter = getFileWriter(invariantFile);
            BufferedWriter writer = new BufferedWriter(fileWriter);
                writer.write(invariantContent);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void createDirectoryIfNeeded(String directory) {
        File file = new File(directory);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static FileWriter getFileWriter(String invariantFile) throws IOException {
        File file = new File(invariantFile);
        if (!file.exists()) {
            file.createNewFile();
        }

        return new FileWriter(file);
    }

    private static String invariantTemplate =
            """
            package org.invariantgenerator.invariantlanguage;
                
            import com.fasterxml.jackson.databind.JsonNode;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import org.apache.flink.api.common.eventtime.WatermarkStrategy;
            import org.apache.flink.api.common.typeinfo.TypeInformation;
            import org.apache.flink.cep.CEP;
            import org.apache.flink.cep.functions.PatternProcessFunction;
            import org.apache.flink.cep.functions.TimedOutPartialMatchHandler;
            import org.apache.flink.cep.pattern.Pattern;
            import org.apache.flink.cep.pattern.conditions.IterativeCondition;
            import org.apache.flink.cep.pattern.conditions.SimpleCondition;
            import org.apache.flink.connector.kafka.source.KafkaSource;
            import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
            import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
            import org.apache.flink.streaming.api.datastream.DataStream;
            import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
            import org.apache.flink.streaming.api.functions.sink.*;
            import org.apache.flink.streaming.api.windowing.time.Time;
            import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
            import org.apache.flink.util.Collector;
            import org.apache.flink.util.OutputTag;
            import org.apache.kafka.clients.consumer.ConsumerRecord;
            import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
            import org.apache.flink.connector.kafka.sink.KafkaSink;
            import org.apache.kafka.common.serialization.StringSerializer;
                
            import java.io.IOException;
            import java.nio.charset.StandardCharsets;
            import java.time.Instant;
            import java.time.Duration;
            import java.util.*;
            import java.util.stream.Collectors;
                
            public class InvariantTemplate {
                
                // ${main}
                
                // ${DataStreamCode}
                
                public static void checkInvariant(
                        StreamExecutionEnvironment env,
                        DataStream<Event> input)
                        throws Exception {
                
                    var patternStream = CEP.pattern(input, invariant);
                    var matches =
                            patternStream
                                    .inEventTime()
                            ;// ${process}
                
                
                    // ${kafkaSink}
                
                
                    env.execute("Test invariant");
                }
                
                // ${MyPatternProcessFunction}
                
                public static Pattern<Event, ?> invariant;
                
                
                public static KafkaSource<Event> getEventKafkaSourceAuthenticated(String broker, String topic, String groupId, String username, String password) {
                    return KafkaSource.<Event>builder()
                            .setBootstrapServers(broker)
                            .setTopics(topic)
                            .setProperty("security.protocol", "SASL_SSL")
                            .setProperty("sasl.mechanism", "PLAIN")
                            .setProperty("sasl.jaas.config",
                                    String.format(
                                            "org.apache.kafka.common.security.scram.ScramLoginModule required username=\\"%s\\" password=\\"%s\\";",
                                            username, password)
                            )
                            .setGroupId(groupId)
                            .setStartingOffsets(OffsetsInitializer.latest())
                            .setDeserializer(
                                    KafkaRecordDeserializationSchema.of(
                                            new KafkaDeserializationSchema<Event>() {
                                                @Override
                                                public boolean isEndOfStream(Event event) {
                                                    return false;
                                                }
                
                                                @Override
                                                public Event deserialize(
                                                        ConsumerRecord<byte[], byte[]> consumerRecord)
                                                        throws Exception {
                                                    String key = new String(consumerRecord.key(), StandardCharsets.UTF_8);
                                                    String value = new String(consumerRecord.value(), StandardCharsets.UTF_8);
                                                    ObjectMapper objectMapper = new ObjectMapper();
                                                    JsonNode jsonNode = objectMapper.readTree(value);
                                                    return new Event(key, jsonNode);
                                                }
                
                                                @Override
                                                public TypeInformation<Event> getProducedType() {
                                                    return TypeInformation.of(Event.class);
                                                }
                                            }))
                            .build();
                }
                
                public static KafkaSink<String> createKafkaSink(String broker, String topic, String username, String password) {
                    var kafkaProducerConfig = new Properties();
                    kafkaProducerConfig.setProperty("security.protocol", "SASL_SSL");
                    kafkaProducerConfig.setProperty("sasl.mechanism", "PLAIN");
                    kafkaProducerConfig.setProperty("sasl.jaas.config",
                            String.format(
                                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\\"%s\\" password=\\"%s\\";",
                                    username, password)
                    );
                    return KafkaSink.<String>builder()
                            .setBootstrapServers(broker)
                            .setKafkaProducerConfig(kafkaProducerConfig)
                            .setRecordSerializer(
                                    KafkaRecordSerializationSchema.builder()
                                            .setTopic(topic)
                                            .setKafkaValueSerializer(StringSerializer.class)
                                            .build())
                            .build();
                }
                
                public static class Event {
                    public String Type;
                    public JsonNode Content;
                    public Event() {
                    }
                    public Event(String type, JsonNode content) {
                        Type = type;
                        Content = content;
                    }
                
                    public Event(String type, String content) {
                        Type = type;
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            Content = objectMapper.readTree(content);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                
                    @Override
                    public String toString() {
                        return "Event{" + "Type='" + Type + '\\'' + ", Content=" + Content + '}';
                    }
                }
                
            }
            """;
}

