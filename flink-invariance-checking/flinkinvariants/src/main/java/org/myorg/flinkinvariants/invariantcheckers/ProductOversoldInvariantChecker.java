package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.FileReader;
import org.myorg.flinkinvariants.events.EshopRecord;

public class ProductOversoldInvariantChecker {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        var streamSource = FileReader.GetDataStreamSource(env, "/src/oversold_2.json");

        var relevantEvents = streamSource.filter((FilterFunction<EshopRecord>) record ->
                record.EventName.equals("ProductCreatedIntegrationEvent")
             || record.EventName.equals("ProductStockChangedIntegrationEvent")
             || record.EventName.equals("ProductDeletedIntegrationEvent")
             || record.EventName.equals("ProductBoughtIntegrationEvent")).setParallelism(1);

        var violations = relevantEvents
                .keyBy(r -> r.EventBody.get("ProductId"))
                .flatMap(new OversoldMapper());
        violations.print();

        System.out.println("Started Flink query for Oversold Invariant..");
        env.execute("Flink Eshop Product Oversold Invariant");
    }

    static class OversoldMapper extends RichFlatMapFunction<EshopRecord, String> {

        /** The state for the current key. */
        private ValueState<Integer> currentStock;

        @Override
        public void open(Configuration conf) {
            // get access to the state object
            currentStock =
                    getRuntimeContext().getState(new ValueStateDescriptor<>("ProductStock", Integer.class));
        }

        @Override
        public void flatMap(EshopRecord evt, Collector<String> out) throws Exception {
            // get the current stock for the key (Product)
            Integer stock = currentStock.value();
            var productId = evt.EventBody.get("ProductId").asText();

            switch (evt.EventName) {
                case "ProductCreatedIntegrationEvent":
                    currentStock.update(evt.EventBody.get("AvailableStock").asInt());
                    return;
                case "ProductStockChangedIntegrationEvent":
                    currentStock.update(evt.EventBody.get("NewStock").asInt());
                    return;
                case "ProductDeletedIntegrationEvent":
                    currentStock.update(null);
                    return;
                case "ProductBoughtIntegrationEvent":
                    // null signals product is deleted
                    if (stock == null) {
                        out.collect("Violation: Bought deleted Item with Id: " + productId);
                        return;
                    }
                    var unitsBought = evt.EventBody.get("Units").asInt();
                    if (unitsBought < 0) {
                        out.collect("Violation: units bought negative for ProductId: " + productId
                                + ", units bought: " + unitsBought);
                        return;
                    }
                    // check if current stock allows
                    if (stock < unitsBought) {
                        out.collect("Violation: stock not sufficient for ProductId: " + productId
                                + ", current stock: " + stock
                                + ", units bought: " + unitsBought);

                        // TODO: should we set stock to zero here?
                    } else {
                        currentStock.update(stock - unitsBought);
                    }
            }
        }
    }

}
