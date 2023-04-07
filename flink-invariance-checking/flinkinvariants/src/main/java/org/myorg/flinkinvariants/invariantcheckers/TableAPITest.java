package org.myorg.flinkinvariants.invariantcheckers;


import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EventType;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class TableAPITest {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);

        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        var streamSource = FileReader.GetDataStreamSource(env, "/src/oversold_3.json").assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                        forBoundedOutOfOrderness(Duration.ofSeconds(30))
                .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));

        Table table =
                tableEnv.fromDataStream(
                        streamSource,
                        Schema.newBuilder()
                                .columnByMetadata("rowtime", "TIMESTAMP_LTZ(3)")
                                .watermark("rowtime", "SOURCE_WATERMARK()")
                                .build());
        tableEnv.createTemporaryView("events", table);
        Table sorted = tableEnv.sqlQuery("SELECT * FROM events ORDER BY rowtime ASC");
        DataStream<EShopIntegrationEvent> sortedStream = tableEnv.toDataStream(sorted).map(r ->
                new EShopIntegrationEvent(r.getFieldAs(0), r.getFieldAs(1), r.getFieldAs(2)))
                .setParallelism(1);


        sortedStream.print();

        System.out.println("Started Flink query for Oversold Invariant..");
        env.execute("Flink Eshop Product Oversold Invariant");
    }
}
