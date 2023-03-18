package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.events.EshopRecord;

import javax.xml.crypto.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.myorg.flinkinvariants.Connectors.getEshopRecordKafkaSource;

public class CEPSimpleFilterJob {

    public static void main(String[] args) throws Exception {
        String broker = "localhost:29092";
        String topic = "eshop_event_bus";
        String groupId = "flink-invariant-checker";

        // Sets up the execution environment, which is the main entry point
        // to building Flink applications.
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        KafkaSource<EshopRecord> source = getEshopRecordKafkaSource(broker, topic, groupId);
        DataStreamSource<EshopRecord> input = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        Pattern<EshopRecord, ?> priceChange = Pattern.<EshopRecord>begin("priceChange")
                .where(new SimpleCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record){
                        return record.EventName.equals("ProductPriceChangedIntegrationEvent");
                    }
                });

        Pattern<EshopRecord, ?> userCheckout = Pattern.<EshopRecord>begin("userCheckout")
                .where(new SimpleCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record) {
                        return record.EventName.equals("UserCheckoutAcceptedIntegrationEvent");
                    }
                });

        Pattern<EshopRecord, ?> priceInvariant = priceChange.followedBy("userCheckout")
                .where(new IterativeCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record, IterativeCondition.Context<EshopRecord> ctx) throws Exception {
                        return true;
                    }
                });

        DataStream<String> result = CEP.pattern(input, priceInvariant)
                .inProcessingTime()
                .flatSelect(
                        (p, o) -> {
                            StringBuilder builder = new StringBuilder();

                            builder.append(p.get("priceChange").get(0));
                            builder.append("\n");
                            builder.append(p.get("userCheckout").get(0));

                            o.collect(builder.toString());
                        },
                        Types.STRING);
        result.print();


        // Execute program, beginning computation.
        env.execute("Flink Eshop Product Price Changed Invariant");
    }
}
