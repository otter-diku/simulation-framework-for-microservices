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
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.Event;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class KafkaReader {
    private static final String broker = "localhost:29092";
    private static final String topic = "eshop_event_bus";
    private static final String groupId = "flink-invariant-checker";

    public static DataStreamSource<EShopIntegrationEvent> GetDataStreamSource(StreamExecutionEnvironment env) {
        KafkaSource<EShopIntegrationEvent> source = getEshopRecordKafkaSource();
        return env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
    }


    public static DataStreamSource<Event> GetEventDataStreamSource(StreamExecutionEnvironment env, String topic, String groupId) {
        KafkaSource<Event> source = getEventKafkaSource(topic, groupId);
        return env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
    }

    public static DataStreamSource<EShopIntegrationEvent> GetDataStreamSourceEventTime(StreamExecutionEnvironment env, Duration boundForOutOfOrderness) {
        KafkaSource<EShopIntegrationEvent> source = getEshopRecordKafkaSource();
        return env.fromSource(source,
                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(boundForOutOfOrderness)
                        .withTimestampAssigner((event, timestamp) -> event.getEventTime()), "Kafka Source");
    }

    private static KafkaSource<EShopIntegrationEvent> getEshopRecordKafkaSource() {

        return KafkaSource.<EShopIntegrationEvent>builder()
                .setBootstrapServers(KafkaReader.broker)
                .setTopics(KafkaReader.topic)
                .setGroupId(KafkaReader.groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(KafkaRecordDeserializationSchema.of(new KafkaDeserializationSchema<EShopIntegrationEvent>() {
                    @Override
                    public boolean isEndOfStream(EShopIntegrationEvent eshopIntegrationEvent) {
                        return false;
                    }

                    @Override
                    public EShopIntegrationEvent deserialize(ConsumerRecord<byte[], byte[]> consumerRecord) throws Exception {
                        String key = new String(consumerRecord.key(), StandardCharsets.UTF_8);
                        String value = new String(consumerRecord.value(), StandardCharsets.UTF_8);
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(value);
                        return new EShopIntegrationEvent(key, jsonNode,  Instant.parse(jsonNode.get("CreationDate").asText()).toEpochMilli());
                    }

                    @Override
                    public TypeInformation<EShopIntegrationEvent> getProducedType() {
                        return TypeInformation.of(EShopIntegrationEvent.class);
                    }
                }))
                .build();
    }

    private static KafkaSource<Event> getEventKafkaSource(String topic, String groupId) {

        return KafkaSource.<Event>builder()
                .setBootstrapServers(KafkaReader.broker)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(KafkaRecordDeserializationSchema.of(new KafkaDeserializationSchema<Event>() {
                    @Override
                    public boolean isEndOfStream(Event event) {
                        return false;
                    }

                    @Override
                    public Event deserialize(ConsumerRecord<byte[], byte[]> consumerRecord) throws Exception {
                        String value = new String(consumerRecord.value(), StandardCharsets.UTF_8);
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(value);
                        return new Event(topic, jsonNode);
                    }

                    @Override
                    public TypeInformation<Event> getProducedType() {
                        return TypeInformation.of(Event.class);
                    }
                }))
                .build();
    }
}