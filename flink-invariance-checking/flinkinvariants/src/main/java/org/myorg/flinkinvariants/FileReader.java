package org.myorg.flinkinvariants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileReader {

    public static DataStreamSource<EshopRecord> GetDataStreamSource(StreamExecutionEnvironment env, String file) {
        var list = GetEshopEventsFromFile(file);
        return env.fromElements(list.toArray(new EshopRecord[0]));
    }

    private static List<EshopRecord> GetEshopEventsFromFile(String file) {
        String content = GetFileContentAsString(file);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(content);

            List<EshopRecord> events = new ArrayList<>();
            for (Iterator<JsonNode> it = jsonNode.elements(); it.hasNext(); ) {
                var elem = it.next();
                // TODO: find better solution for this
                if (elem.has("NewPrice")) {
                    events.add(new EshopRecord(ProductPriceChangedIntegrationEvent.EventName, elem));
                } else if (elem.has("PictureUri")) {
                    events.add(new EshopRecord(ProductCreatedIntegrationEvent.EventName, elem));
                } else if (elem.has("NewStock")) {
                    events.add(new EshopRecord(ProductStockChangedIntegrationEvent.EventName, elem));
                } else if (elem.has("Units")) {
                    events.add(new EshopRecord(ProductBoughtIntegrationEvent.EventName, elem));
                } else if (elem.has("ProductId")) {
                    events.add(new EshopRecord(ProductDeletedIntegrationEvent.EventName, elem));
                } else if (elem.get("Type").asText().equals("OrderStatusChangedToSubmittedIntegrationEvent")) {
                    events.add(new EshopRecord(OrderStatusChangedToSubmittedIntegrationEvent.EventName, elem));
                } else if (elem.get("Type").asText().equals("OrderPaymentFailedIntegrationEvent")) {
                    events.add(new EshopRecord(OrderPaymentFailedIntegrationEvent.EventName, elem));
                } else if (elem.get("Type").asText().equals("OrderPaymentSucceededIntegrationEvent")) {
                    events.add(new EshopRecord(OrderPaymentSucceededIntegrationEvent.EventName, elem));
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
