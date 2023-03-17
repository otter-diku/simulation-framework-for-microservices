package org.myorg.flinkinvariants;

import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;

import static org.myorg.flinkinvariants.Connectors.getEshopRecordKafkaSource;

public class CEPEshopProductPriceChangedCheckout {

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
                        // Get basket from user checkout event

                        // for (EshopRecord record : ctx.getEventsForPattern("priceChange")) {}

                        // return Double.compare(sum, 5.0) < 0;
                        return false;
                    }
                });





        // Execute program, beginning computation.
        env.execute("Flink Eshop Invariant Checker");
    }
}
