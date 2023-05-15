package org.invariantchecker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.invariantgenerator.invariantlanguage.InvariantGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Client {

    static Options options = null;
    static Option queueConfigOption = null;
    static Option invariantOption = null;

    static InvariantGenerator invariantGenerator = null;

    private static void addOptions() {
        options = new Options();

        invariantOption = Option.builder("i")
                .argName("invariants")
                .longOpt("invariants")
                .hasArg()
                .desc("directory with the invariants")
                .build();

        queueConfigOption = Option.builder("q")
                .argName("queueConfig")
                .longOpt("queue-config")
                .hasArg()
                .desc("JSON file with the queue config")
                .optionalArg(true)
                .build();

        options.addOption(invariantOption);
        options.addOption(queueConfigOption);
    }

    public static void main(String[] args) {
        addOptions();

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(options, args);
            if (!line.hasOption("invariants")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Invalid usage of invariant_checker", options);
            }

            var invariantsDir = line.getOptionValue("invariants");
            var queueConfig = Optional.ofNullable(line.getOptionValue("queue-config"));

            System.out.printf("Invariants directory: %s\nQueue config: %s",
                    invariantsDir,
                    queueConfig.orElse("default"));

            processInvariants(invariantsDir, queueConfig.get());

        }
        catch (ParseException exception) {
            System.err.println("Parsing failed. Reason: " + exception.getMessage());
        }
    }

    /**
     * Reads invariantQuery files and queue config and generates corresponding
     * java code for invariants + pom.xml such that these can be directly compiled
     * to jar packages and uploaded to a flink cluster.
     * @param invariantsDir
     * @param queueConfigFilePath
     */
    private static void processInvariants(String invariantsDir, String queueConfigFilePath) {
        invariantGenerator = new InvariantGenerator();

        try {
            var queueConfigContent =  Files.readString(Paths.get(queueConfigFilePath), StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            Map<String,Object> queueConfig =
                    new ObjectMapper().readValue(queueConfigContent, HashMap.class);
             var invariantFiles = Files.walk(Paths.get(invariantsDir))
                     .filter(Files::isRegularFile)
                     .filter(
                             f -> f.toString().endsWith(".inv")
                     )
                     .collect(Collectors.toList());

            for (var invariantFile : invariantFiles) {
                var invariantQuery =  Files.readString(invariantFile, StandardCharsets.UTF_8);
                invariantGenerator.generateInvariantFile(invariantFile.getFileName().toString().replace(".inv", ""), invariantQuery, queueConfig);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
