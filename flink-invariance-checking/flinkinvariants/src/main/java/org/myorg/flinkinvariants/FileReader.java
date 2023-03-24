package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.EshopRecord;
import org.myorg.flinkinvariants.events.ProductPriceChangedIntegrationEvent;
import org.myorg.flinkinvariants.events.UserCheckoutAcceptedIntegrationEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileReader {

    public static DataStreamSource<EshopRecord> GetDataStreamSource(StreamExecutionEnvironment env, String file) {
        var list = GetBasketFromCheckout(file);
        return env.fromElements(list.toArray(new EshopRecord[0]));
    }

    private static List<EshopRecord> GetBasketFromCheckout(String file) {
        String content = GetFileContentAsString(file);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(content);

            List<EshopRecord> events = new ArrayList<>();
            for (Iterator<JsonNode> it = jsonNode.elements(); it.hasNext(); ) {
                var elem = it.next();
                if (elem.has("NewPrice")) {
                    events.add(new EshopRecord(ProductPriceChangedIntegrationEvent.EventName, elem));
                } else {
                    events.add(new EshopRecord(UserCheckoutAcceptedIntegrationEvent.EventName, elem));
                }
            };

            return events;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String GetFileContentAsString(String file) {
        String fileName = System.getProperty("user.dir") + file;
        try {
            return Files.readString(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
