package org.myorg.flinkinvariants.datastreamsourceproviders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.myorg.flinkinvariants.events.EShopIntegrationEvent;
import org.myorg.flinkinvariants.events.EShopIntegrationEventWrapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class FileReader {

    public static DataStreamSource<EShopIntegrationEvent> GetDataStreamSource(
            StreamExecutionEnvironment env, String file) {
        var list = GetEshopEventsFromFile(file);
        return env.fromElements(list.toArray(new EShopIntegrationEvent[0]));
    }

    public static List<EShopIntegrationEvent> GetEshopEventsFromFile(String file) {
        try {
            String content = GetFileContentAsString(file);
            ObjectMapper objectMapper = new ObjectMapper();
            List<EShopIntegrationEventWrapper> eventWrappers =
                    objectMapper.readValue(
                            content, new TypeReference<List<EShopIntegrationEventWrapper>>() {});

            return eventWrappers.stream()
                    .map(
                            eventWrapper ->
                                    new EShopIntegrationEvent(
                                            eventWrapper.Type,
                                            eventWrapper.Content,
                                            Instant.parse(
                                                            eventWrapper
                                                                    .Content
                                                                    .get("CreationDate")
                                                                    .asText())
                                                    .toEpochMilli()))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String GetFileContentAsString(String file) {
        try {
            String fileName = System.getProperty("user.dir") + file;
            return Files.readString(Paths.get(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
