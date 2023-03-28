package org.myorg.flinkinvariants.invariantcheckers;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class ProductPriceChangedInvariantChecker {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = FileReader.GetDataStreamSource(env, "/src/product_price_changed_invariant_2.json");

        var patternStream = CEP.pattern(streamSource, InvariantPattern);
        var matches = patternStream
                .inProcessingTime()
                .process(new PatternProcessFunction<EShopIntegrationEvent, String>() {
                    @Override
                    public void processMatch(Map<String, List<EShopIntegrationEvent>> map, Context context, Collector<String> collector) {
                        collector.collect(map.toString());
                    }
                });

        matches.print();

        System.out.println("Started CEP query for Price Changed Invariant..");
        env.execute("Flink Eshop Product Price Changed Invariant");
    }

    public static Pattern<EShopIntegrationEvent, ?> InvariantPattern =  Pattern.<EShopIntegrationEvent>begin("firstPriceChange")
            .where(new SimpleCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent eshopIntegrationEvent) {
                    return eshopIntegrationEvent.EventName.equals(EventType.ProductPriceChangedIntegrationEvent.name());
                }
            })
            .notFollowedBy("subsequentPriceChange")
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent subsequentPriceChangeEvent, Context<EShopIntegrationEvent> context) throws Exception {
                    if (!subsequentPriceChangeEvent.EventName.equals(EventType.ProductPriceChangedIntegrationEvent.name()))
                        return false;

                    for (var firstPriceChangeEvent : context.getEventsForPattern("firstPriceChange")) {
                        return firstPriceChangeEvent.EventBody.get("ProductId").asInt() == subsequentPriceChangeEvent.EventBody.get("ProductId").asInt();
                    }

                    return false;
                }
            })
            .followedByAny("userCheckoutWithOutDatedItemPrice")
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(EShopIntegrationEvent userCheckoutEvent, Context<EShopIntegrationEvent> context) throws Exception {
                    if (!userCheckoutEvent.EventName.equals("UserCheckoutAcceptedIntegrationEvent")) {
                        return false;
                    }

                    for (var priceChangeEvent : context.getEventsForPattern("firstPriceChange")) {

                        var affectedProductId = priceChangeEvent.EventBody.get("ProductId").asInt();
                        var newPrice = priceChangeEvent.EventBody.get("NewPrice").asDouble();

                        for (Iterator<JsonNode> it = userCheckoutEvent.EventBody.get("Basket").get("Items").elements(); it.hasNext(); ) {
                            var item = it.next();
                            if (affectedProductId == item.get("ProductId").asInt() && newPrice != item.get("UnitPrice").asDouble())
                                return true;
                        }
                    }

                    return false;
                }
            });
}
