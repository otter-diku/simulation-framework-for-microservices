package org.myorg.flinkinvariants;

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
import org.myorg.flinkinvariants.events.EshopRecord;

import java.nio.charset.StandardCharsets;

public class KafkaReader {
    private static final String broker = "localhost:29092";
    private static final String topic = "eshop_event_bus";
    private static final String groupId = "flink-invariant-checker";

    public static DataStreamSource<EshopRecord> GetDataStreamSource(StreamExecutionEnvironment env) {
        KafkaSource<EshopRecord> source = getEshopRecordKafkaSource();
        return env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");
    }

    private static KafkaSource<EshopRecord> getEshopRecordKafkaSource() {

        return KafkaSource.<EshopRecord>builder()
                .setBootstrapServers(KafkaReader.broker)
                .setTopics(KafkaReader.topic)
                .setGroupId(KafkaReader.groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(KafkaRecordDeserializationSchema.of(new KafkaDeserializationSchema<EshopRecord>() {
                    @Override
                    public boolean isEndOfStream(EshopRecord eshopRecord) {
                        return false;
                    }

                    @Override
                    public EshopRecord deserialize(ConsumerRecord<byte[], byte[]> consumerRecord) throws Exception {
                        String key = new String(consumerRecord.key(), StandardCharsets.UTF_8);
                        String value = new String(consumerRecord.value(), StandardCharsets.UTF_8);
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(value);
                        return new EshopRecord(key, jsonNode);
                    }

                    @Override
                    public TypeInformation<EshopRecord> getProducedType() {
                        return TypeInformation.of(EshopRecord.class);
                    }
                }))
                .build();
    }
}