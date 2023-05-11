package org.invariantchecker.sinks;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.invariantchecker.events.InvariantViolationEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SeqSink extends PrintSinkFunction<InvariantViolationEvent> {

    private final String seqBaseUrl = "http://localhost:5341/api/events/raw?clef";
    private final Duration httpRequestTimeout = Duration.ofMinutes(1);

    private HttpClient client;

    public SeqSink() {
        super();
    }

    public SeqSink(boolean stdErr) {
        super(stdErr);
    }

    public SeqSink(String sinkIdentifier, boolean stdErr) {
        super(sinkIdentifier, stdErr);
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        client = HttpClient.newBuilder().build();
        super.open(parameters);
    }

    @Override
    public void invoke(InvariantViolationEvent record) {
        super.invoke(record);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(seqBaseUrl))
                .timeout(httpRequestTimeout)
                .header("Content-Type", "application/vnd.serilog.clef")
                .POST(HttpRequest.BodyPublishers.ofString(record.toString()))
                .build();
        try {
            var _response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}