package org.myorg.flinkinvariants.invariantcheckers;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;

import java.io.IOException;
import java.time.Duration;

public class ProductOversoldInvariantChecker {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        var streamSource = FileReader.GetDataStreamSource(env, "/src/oversold_1.json").assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                        forBoundedOutOfOrderness(Duration.ofSeconds(30))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));

        var violations = streamSource
                .keyBy(r -> r.EventBody.get("ProductId"))
                .flatMap(new OversoldMapper());

        violations.print().setParallelism(1);

        env.execute("Flink Eshop Product Oversold Invariant");
    }

    public static void CheckOversoldInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<String> sinkFunction
    ) throws Exception {
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        Table table =
                tableEnv.fromDataStream(
                        input,
                        Schema.newBuilder()
                                .columnByMetadata("rowtime", "TIMESTAMP_LTZ(3)")
                                .watermark("rowtime", "SOURCE_WATERMARK()")
                                .build());

        tableEnv.createTemporaryView("events", table);
        Table sorted = tableEnv.sqlQuery("SELECT * FROM events ORDER BY rowtime ASC");

        // TODO: maybe more idiomatic way to convert row datastream instead of
        //  map with parallelism set to 1
        DataStream<EShopIntegrationEvent> sortedStream = tableEnv.toDataStream(sorted).map(r ->
                new EShopIntegrationEvent(r.getFieldAs(0), r.getFieldAs(1), r.getFieldAs(2))).setParallelism(1);

        var violations = sortedStream
                .keyBy(r -> r.EventBody.get("ProductId"))
                .flatMap(new OversoldMapper())
                .addSink(sinkFunction).setParallelism(1);

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
