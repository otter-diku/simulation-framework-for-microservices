package org.myorg.flinkinvariants.invariantcheckers.eshop;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;

import java.time.Duration;

public class TableAPITest {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        var streamSource =
                FileReader.GetDataStreamSource(env, "/src/oversold_3.json")
                        .assignTimestampsAndWatermarks(
                                WatermarkStrategy.<EShopIntegrationEvent>forBoundedOutOfOrderness(
                                                Duration.ofSeconds(30))
                                        .withTimestampAssigner(
                                                (event, timestamp) -> event.getEventTime()));

        Table table =
                tableEnv.fromDataStream(
                        streamSource,
                        Schema.newBuilder()
                                .columnByMetadata("rowtime", "TIMESTAMP_LTZ(3)")
                                .watermark("rowtime", "SOURCE_WATERMARK()")
                                .build());
        tableEnv.createTemporaryView("events", table);
        Table sorted = tableEnv.sqlQuery("SELECT * FROM events ORDER BY rowtime ASC");
        DataStream<EShopIntegrationEvent> sortedStream =
                tableEnv.toDataStream(sorted)
                        .map(
                                r ->
                                        new EShopIntegrationEvent(
                                                r.getFieldAs(0), r.getFieldAs(1), r.getFieldAs(2)))
                        .setParallelism(1);

        sortedStream.print();

        System.out.println("Started Flink query for Oversold Invariant..");
        env.execute("Flink Eshop Product Oversold Invariant");
    }
}
