import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.ClassRule;
import org.junit.Test;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.datastreamsourceproviders.FileReader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.myorg.flinkinvariants.invariantcheckers.ProductPriceChangedInvariantChecker.CheckProductPriceChangedInvariant;

public class InvariantsTest {

    @ClassRule
    public static MiniClusterWithClientResource flinkCluster =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());


    @Test
    public void testProductPriceChangedInvariant1() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = FileReader
                .GetDataStreamSource(env, "/src/product_price_changed_invariant_1.json")
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                                forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));


        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        // assert on violations (string) we expect
        // ViolationSink.values.stream()
    }
    @Test
    public void testProductPriceChangedInvariant2() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        var streamSource = FileReader
                .GetDataStreamSource(env, "/src/product_price_changed_invariant_2.json")
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                                forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));


        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        // assert on violations (string) we expect
        // ViolationSink.values.stream()
    }

    @Test
    public void testProductPriceChangedInvariant3() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // This input tests if event-time is working correctly
        // by having the checkout come first in the file but its timestamp
        // is after first two price changed events
        var streamSource = FileReader
                .GetDataStreamSource(env, "/src/product_price_changed_invariant_3.json")
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                                forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));


        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        // assert on violations (string) we expect
        // ViolationSink.values.stream()
    }

    @Test
    public void testProductPriceChangedInvariant4() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // This input tests if event-time is working correctly
        // by having the checkout come first in the file but its timestamp
        // is after first two price changed events
        var streamSource = FileReader
                .GetDataStreamSource(env, "/src/product_price_changed_invariant_4.json")
                .assignTimestampsAndWatermarks(WatermarkStrategy.<EShopIntegrationEvent>
                                forBoundedOutOfOrderness(Duration.ofSeconds(20))
                        .withTimestampAssigner((event, timestamp) -> event.getTimestamp()));


        CheckProductPriceChangedInvariant(env, streamSource, new ViolationSink());

        // assert on violations (string) we expect
        assertTrue(ViolationSink.values.isEmpty());
    }



    private static class ViolationSink implements SinkFunction<String> {

        // must be static
        public static final List<String> values = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void invoke(String value, SinkFunction.Context context) throws Exception {
            values.add(value);
        }
    }
}

