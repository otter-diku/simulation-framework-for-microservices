package org.invariantchecker.datastreamsourceproviders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.invariantchecker.events.Event;

import java.nio.charset.StandardCharsets;

public class KafkaReader {

    public static KafkaSource<Event> getEventKafkaSource(String broker, String topic, String groupId) {

        return KafkaSource.<Event>builder()
                .setBootstrapServers(broker)
                .setTopics(topic)
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

    public static KafkaSource<Event> getEventKafkaSourceAuthenticated(String broker, String topic, String username, String password) {
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
                .setGroupId("invariant-checker")
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
}
