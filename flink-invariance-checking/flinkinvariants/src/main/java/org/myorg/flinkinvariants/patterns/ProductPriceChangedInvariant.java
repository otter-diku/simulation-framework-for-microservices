package org.myorg.flinkinvariants.patterns;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.myorg.flinkinvariants.events.ProductPriceChangedIntegrationEvent;

import java.util.Iterator;

public class ProductPriceChangedInvariant {

    public static Pattern<EshopRecord, ?> InvariantPattern =  Pattern.<EshopRecord>begin("firstPriceChange")
            .where(new SimpleCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord eshopRecord) throws Exception {
                    return eshopRecord.EventName.equals(ProductPriceChangedIntegrationEvent.EventName);
                }
            })
            .notFollowedBy("subsequentPriceChange")
            .where(new IterativeCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord subsequentPriceChangeEvent, Context<EshopRecord> context) throws Exception {
                    if (!subsequentPriceChangeEvent.EventName.equals(ProductPriceChangedIntegrationEvent.EventName))
                        return false;

                    for (var firstPriceChangeEvent : context.getEventsForPattern("firstPriceChange")) {
                        return firstPriceChangeEvent.EventBody.get("ProductId").asInt() == subsequentPriceChangeEvent.EventBody.get("ProductId").asInt();
                    }

                    return false;
                }
            })
            .followedByAny("userCheckoutWithOutDatedItemPrice")
            .where(new IterativeCondition<EshopRecord>() {
                @Override
                public boolean filter(EshopRecord userCheckoutEvent, Context<EshopRecord> context) throws Exception {
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
