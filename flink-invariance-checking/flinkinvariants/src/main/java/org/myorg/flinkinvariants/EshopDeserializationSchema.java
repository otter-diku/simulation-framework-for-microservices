package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.myorg.flinkinvariants.events.EshopRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class EshopDeserializationSchema implements KafkaRecordDeserializationSchema<EshopRecord>
{
    @Override
    public void deserialize(ConsumerRecord consumerRecord, Collector collector) throws IOException {
        String key = new String((byte[]) consumerRecord.key(), StandardCharsets.UTF_8);
        String value = new String((byte[]) consumerRecord.value(), StandardCharsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(value);
        collector.collect(new EshopRecord(key, jsonNode));
    }

    @Override
    public TypeInformation getProducedType() {
        return TypeInformation.of(EshopRecord.class);
    }
}


