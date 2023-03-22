package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.EshopRecord;

import java.util.ArrayList;
import java.util.List;

import static org.myorg.flinkinvariants.Connectors.getEshopRecordKafkaSource;


public class CEPEshopProductPriceFixed {
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

        Pattern<EshopRecord, ?> priceChange2 = Pattern.<EshopRecord>begin("priceChange2")
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

        Pattern<EshopRecord, ?> priceInvariant = priceChange.notFollowedBy("priceChange2").followedBy("userCheckout")
                .where(new IterativeCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record, IterativeCondition.Context<EshopRecord> ctx) throws Exception {
                        // Get basket items from user checkout event
                        if (!record.EventName.equals("UserCheckoutAcceptedIntegrationEvent")) {
                            return false;
                        }

                        List<JsonNode> items = new ArrayList<>();
                        record.EventBody.get("Basket").get("Items").forEach(items::add);

                        // TODO: assert we use latest priceChanged event
                        EshopRecord priceChanged =  ctx.getEventsForPattern("priceChange")
                                .iterator().next();

                        // Get productId and new price
                        int productId = priceChanged.EventBody.get("ProductId").asInt();
                        double newPrice = priceChanged.EventBody.get("NewPrice").asDouble();

                        for (JsonNode item: items) {
                            if (item.get("ProductId").asInt() == productId) {
                                return item.get("UnitPrice").asDouble() != newPrice;
                            }
                        }
                        return false;
                    }
                });

        DataStream<String> violations = CEP.pattern(input, priceInvariant)
                .inProcessingTime()
                .flatSelect(
                        (p, o) -> {
                            StringBuilder builder = new StringBuilder();

                            builder.append("Violation of Price changed Invariant : ");
                            builder.append(p.get("priceChange").get(0));
                            builder.append("\n");
                            builder.append("Checkout Items: ");
                            builder.append(p.get("userCheckout").get(0).EventBody.get("Basket").get("Items"));

                            o.collect(builder.toString());
                        },
                        Types.STRING);

        violations.print();

        // Execute program, beginning computation.
        System.out.println("Started CEP query for Price Changed Invariant..");
        env.execute("Flink Eshop Product Price Changed Invariant");
    }
}

