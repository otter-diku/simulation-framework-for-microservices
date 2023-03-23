package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.events.EshopRecord;

import java.util.*;
import java.util.stream.Collectors;

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

        Pattern<EshopRecord, ?> _priceChange2 = Pattern.<EshopRecord>begin("priceChange2")
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

        // productPrice  -> ... -> userCheckout
        Pattern<EshopRecord, ?> priceInvariant = priceChange
                .notFollowedBy("priceChange2")
                .where(new IterativeCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record, Context<EshopRecord> ctx) throws Exception {
                        if (!record.EventName.equals("ProductPriceChangedIntegrationEvent")) {
                            return true;
                        }
                        int productId = record.EventBody.get("ProductId").asInt();

                        for (EshopRecord event : ctx.getEventsForPattern("priceChange")) {
                            int knownProductId = event.EventBody.get("ProductId").asInt();
                            if (productId == knownProductId) {
                                return false;
                            }
                        }
                        return true;
                    }
                })
                .followedByAny("userCheckout")
                .where(new IterativeCondition<EshopRecord>() {
                    @Override
                    public boolean filter(EshopRecord record, IterativeCondition.Context<EshopRecord> ctx) throws Exception {
                        // Get basket items from user checkout event
                        if (!record.EventName.equals("UserCheckoutAcceptedIntegrationEvent")) {
                            return false;
                        }

                        List<JsonNode> items = new ArrayList<>();
                        record.EventBody.get("Basket").get("Items").forEach(items::add);

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
                            builder.append("\n");
                            builder.append("PriceChanged timestamp: ");
                            builder.append(p.get("priceChange").get(0).EventBody.get("CreationDate"));
                            builder.append(" " + p.get("priceChange").get(0).EventBody);

                            builder.append("\n");
                            builder.append("UserCheckout timestamp: ");
                            builder.append(p.get("userCheckout").get(0).EventBody.get("CreationDate"));
                            builder.append(" " + p.get("userCheckout").get(0).EventBody.get("Basket").get("Items"));

                            o.collect(builder.toString());
                        },
                        Types.STRING);

        DataStream<String> violationsCleaned = CEP.pattern(input, priceInvariant)
                .inProcessingTime()
                .flatSelect(
                        (p, o) -> {
                            o.collect(p.get("priceChange").get(0).EventBody.get("Id") + "|" +
                            p.get("userCheckout").get(0).EventBody.get("Id"));
                        },
                        Types.STRING);

        /*
        Violation of Price changed Invariant :
PriceChanged timestamp: "2023-03-23T20:08:25.4135012Z" {"ProductId":4,"NewPrice":24,"OldPrice":158.0,"Id":"97566939-aed1-44c9-8b2e-de702943bc18","CreationDate":"2023-03-23T20:08:25.4135012Z"}
UserCheckout timestamp: "2023-03-23T20:08:25.48225Z" [{"Id":"1","ProductId":4,"ProductName":".NET Foundation T-shirt","UnitPrice":158.0,"OldUnitPrice":158.0,"Quantity":1,"PictureUrl":""},{"Id":"2","ProductId":8,"ProductName":"Kudu Purple Hoodie","UnitPrice":44.0,"OldUnitPrice":44.0,"Quantity":1,"PictureUrl":""},{"Id":"3","ProductId":9,"ProductName":"Cup<T> White Mug","UnitPrice":187.0,"OldUnitPrice":187.0,"Quantity":1,"PictureUrl":""}]
         */
        // violations.print();

        // dictionary -> list of user checkout
        DataStream<String> p =  violationsCleaned
                .keyBy(s -> s.split("|")[0])
                .window(TumblingProcessingTimeWindows.of(Time.seconds(15)))
                .process(new MyProcessWindowFunction());

        p.print();

        // Execute program, beginning computation.
        System.out.println("Started CEP query for Price Changed Invariant..");
        env.execute("Flink Eshop Product Price Changed Invariant");
    }

    public static class MyProcessWindowFunction
            extends ProcessWindowFunction<String, String, String, TimeWindow> {

        @Override
        public void process(String key, Context context, Iterable<String> input, Collector<String> out) {

            long count = 0;
            Map<String, List<String>> idMap = new HashMap<>();

            for (String in: input) {

                String productId = in.split("|")[0];
                String userCheckoutId = in.split("|")[1];

                if (idMap.containsKey(productId)) {
                    idMap.get(productId).add(userCheckoutId);
                } else {
                    idMap.put(productId, new ArrayList<>());
                    idMap.get(productId).add(userCheckoutId);
                }
            }
            out.collect(idMap.entrySet().stream().map(
                    stringListEntry -> {

                        return stringListEntry.getKey() + ":" +
                                stringListEntry.getValue().stream().map(Object::toString)
                                         .collect(Collectors.joining(", "));
                    }
            ).collect(Collectors.joining("\n")));
        }
    }
}

