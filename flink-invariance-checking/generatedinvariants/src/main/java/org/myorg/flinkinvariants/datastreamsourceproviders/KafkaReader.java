package org.myorg.flinkinvariants.datastreamsourceproviders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.myorg.flinkinvariants.events.Event;

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
}
