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
                                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                                username, password)
                )
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
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

    public static KafkaSink<String> createKafkaSink(String broker, String username, String password) {
        var kafkaProducerConfig = new Properties();
        kafkaProducerConfig.setProperty("security.protocol", "SASL_SSL");
        kafkaProducerConfig.setProperty("sasl.mechanism", "PLAIN");
        kafkaProducerConfig.setProperty("sasl.jaas.config",
                String.format(
                        "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                        username, password)
        );
        return KafkaSink.<String>builder()
                .setBootstrapServers(broker)
                .setKafkaProducerConfig(kafkaProducerConfig)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("violations")
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
            return "Event{" + "Type='" + Type + '\'' + ", Content=" + Content + '}';
        }
    }

}
