package org.invarianttranslator.datastreamsourceproviders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.invarianttranslator.events.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class FileReader {

    public static DataStreamSource<Event> GetDataStreamSource(
            StreamExecutionEnvironment env, String file) {
        var list = GetEshopEventsFromFile(file);
        return env.fromElements(list.toArray(new Event[0]));
    }

    public static List<Event> GetEshopEventsFromFile(String file) {
        try {
            String content = GetFileContentAsString(file);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(content, new TypeReference<List<Event>>() {});

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
