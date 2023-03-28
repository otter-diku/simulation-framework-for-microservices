package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;

import java.io.IOException;

public class ProductOversoldInvariantChecker {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource = FileReader.GetDataStreamSource(env, "/src/oversold_2.json");

        var violations = streamSource
                .keyBy(r -> r.EventBody.get("ProductId"))
                .flatMap(new OversoldMapper());
        violations.print();

        System.out.println("Started Flink query for Oversold Invariant..");
        env.execute("Flink Eshop Product Oversold Invariant");
    }

    static class OversoldMapper extends RichFlatMapFunction<EShopIntegrationEvent, String> {

        /** The state for the current key. */
        private ValueState<Integer> currentStock;

        @Override
        public void open(Configuration conf) {
            // get access to the state object
            currentStock =
                    getRuntimeContext().getState(new ValueStateDescriptor<>("ProductStock", Integer.class));
        }

        @Override
        public void flatMap(EShopIntegrationEvent event, Collector<String> collector) throws Exception {
            // get the current stock for the key (Product)
            var productId = event.EventBody.get("ProductId").asText();
            var eventType = EventType.valueOf(event.EventName);

            switch (eventType) {
                case ProductCreatedIntegrationEvent -> currentStock.update(event.EventBody.get("AvailableStock").asInt());
                case ProductStockChangedIntegrationEvent -> currentStock.update(event.EventBody.get("NewStock").asInt());
                case ProductDeletedIntegrationEvent -> currentStock.update(null);
                case ProductBoughtIntegrationEvent -> HandleProductBoughtIntegrationEvent(event, collector, productId);
            }
        }

        private void HandleProductBoughtIntegrationEvent(EShopIntegrationEvent event, Collector<String> collector, String productId) throws IOException {
            Integer stock = currentStock.value();
            // null signals product is deleted
            if (stock == null) {
                collector.collect("Violation: Bought deleted Item with Id: " + productId);
                return;
            }
            var unitsBought = event.EventBody.get("Units").asInt();
            if (unitsBought < 0) {
                collector.collect("Violation: units bought negative for ProductId: " + productId
                        + ", units bought: " + unitsBought);
                return;
            }
            // check if current stock allows
            if (stock < unitsBought) {
                collector.collect("Violation: stock not sufficient for ProductId: " + productId
                        + ", current stock: " + stock
                        + ", units bought: " + unitsBought);

                // TODO: should we set stock to zero here?
            } else {
                currentStock.update(stock - unitsBought);
            }
        }
    }

}
