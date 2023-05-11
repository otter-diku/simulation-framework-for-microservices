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
    private static final String broker = "localhost:29092";
    private static final String topic = "eshop_event_bus";
    private static final String groupId = "flink-invariant-checker";


    private static KafkaSource<Event> getEventKafkaSource(String topic, String groupId) {

        return KafkaSource.<Event>builder()
                .setBootstrapServers(KafkaReader.broker)
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
                                        String value =
                                                new String(
                                                        consumerRecord.value(),
                                                        StandardCharsets.UTF_8);
                                        ObjectMapper objectMapper = new ObjectMapper();
                                        JsonNode jsonNode = objectMapper.readTree(value);
                                        return new Event(topic.replace("-queue", ""), jsonNode);
                                    }

                                    @Override
                                    public TypeInformation<Event> getProducedType() {
                                        return TypeInformation.of(Event.class);
                                    }
                                }))
                .build();
    }
}
