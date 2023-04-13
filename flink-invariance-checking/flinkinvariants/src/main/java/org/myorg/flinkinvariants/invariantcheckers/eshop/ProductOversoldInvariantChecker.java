package org.myorg.flinkinvariants.invariantcheckers.eshop;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.KafkaReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;
import org.myorg.flinkinvariants.events.InvariantViolationEvent;
import org.myorg.flinkinvariants.sinks.SeqSink;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class ProductOversoldInvariantChecker {

    private static final int MAX_LATENESS_OF_EVENT = 30;

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);

        var streamSource = KafkaReader.GetDataStreamSource(env)
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(Duration.ofSeconds(MAX_LATENESS_OF_EVENT))
                        //.withIdleness(Duration.ofSeconds(2))
                        .withTimestampAssigner((event, timestamp) -> event.getEventTime()));


        CheckOversoldInvariant(env, streamSource, new SeqSink());
    }

    public static void CheckOversoldInvariant(
            StreamExecutionEnvironment env,
            DataStream<EShopIntegrationEvent> input,
            SinkFunction<InvariantViolationEvent> sinkFunction
    ) throws Exception {
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);


        var filteredInput = input.filter((FilterFunction<EShopIntegrationEvent>) record ->
                        record.getEventName().equals(EventType.ProductCreatedIntegrationEvent.name())
                     || record.getEventName().equals(EventType.ProductDeletedIntegrationEvent.name())
                     || record.getEventName().equals(EventType.ProductBoughtIntegrationEvent.name())
                     || record.getEventName().equals(EventType.ProductStockChangedIntegrationEvent.name()))
                .setParallelism(1);

        Table table =
                tableEnv.fromDataStream(
                        filteredInput,
                        Schema.newBuilder()
                                .columnByMetadata("rowtime", "TIMESTAMP_LTZ(3)")
                                .watermark("rowtime", "SOURCE_WATERMARK()")
                                .build());

        tableEnv.createTemporaryView("events", table);
        Table sorted = tableEnv.sqlQuery("SELECT * FROM events ORDER BY rowtime ASC");

        DataStream<EShopIntegrationEvent> sortedStream = tableEnv.toDataStream(sorted).map(
                new MapFunction<Row, EShopIntegrationEvent>() {
                    @Override
                    public EShopIntegrationEvent map(Row row) throws Exception {
                        return new EShopIntegrationEvent(row.getFieldAs(0), row.getFieldAs(1), row.getFieldAs(2));
                    }
                }).setParallelism(1);

        var violations = sortedStream
                .keyBy(r -> r.getEventBody().get("ProductId"))
                .flatMap(new OversoldMapper())
                .addSink(sinkFunction).setParallelism(1);

        env.execute("Flink Eshop Product Oversold Invariant");
    }

    static class OversoldMapper extends RichFlatMapFunction<EShopIntegrationEvent, InvariantViolationEvent> {

        /** The state for the current key. */
        private ValueState<Integer> currentStock;

        @Override
        public void open(Configuration conf) {
            // get access to the state object
            currentStock =
                    getRuntimeContext().getState(new ValueStateDescriptor<>("ProductStock", Integer.class));
        }

        @Override
        public void flatMap(EShopIntegrationEvent event, Collector<InvariantViolationEvent> collector) throws Exception {
            // get the current stock for the key (Product)
            var productId = event.getEventBody().get("ProductId").asText();
            var eventType = EventType.valueOf(event.getEventName());

            switch (eventType) {
                case ProductCreatedIntegrationEvent -> currentStock.update(event.getEventBody().get("AvailableStock").asInt());
                case ProductStockChangedIntegrationEvent -> currentStock.update(event.getEventBody().get("NewStock").asInt());
                case ProductDeletedIntegrationEvent -> currentStock.update(null);
                case ProductBoughtIntegrationEvent -> HandleProductBoughtIntegrationEvent(event, collector, productId);
            }
        }

        private void HandleProductBoughtIntegrationEvent(EShopIntegrationEvent event, Collector<InvariantViolationEvent> collector, String productId) throws IOException {
            Integer stock = currentStock.value();
            // null signals product is deleted
            if (stock == null) {
                collector.collect(new InvariantViolationEvent(event.getEventTimeAsString(),
                        "{InvariantName} invariant violated - {SubType}",
                        Map.of("InvariantName", "ProductOversoldInvariant", "SubType", "Bought deleted item")));
                // collector.collect("Violation: Bought deleted Item with Id: " + productId);
                return;
            }
            var unitsBought = event.getEventBody().get("Units").asInt();
            if (unitsBought < 0) {
                collector.collect(new InvariantViolationEvent(event.getEventTimeAsString(),
                        "{InvariantName} invariant violated - {SubType}",
                        Map.of("InvariantName", "ProductOversoldInvariant", "SubType", "Bought negative number of units")));

//                collector.collect("Violation: units bought negative for ProductId: " + productId
//                        + ", units bought: " + unitsBought);
                return;
            }
            // check if current stock allows
            if (stock < unitsBought) {

                collector.collect(new InvariantViolationEvent(event.getEventTimeAsString(),
                        "{InvariantName} invariant violated - {SubType}",
                        Map.of("InvariantName", "ProductOversoldInvariant", "SubType", "Stock not sufficient")));

//                collector.collect("Violation: stock not sufficient for ProductId: " + productId
//                        + ", current stock: " + stock
//                        + ", units bought: " + unitsBought);

                // TODO: should we set stock to zero here?
            } else {
                currentStock.update(stock - unitsBought);
            }
        }
    }

}