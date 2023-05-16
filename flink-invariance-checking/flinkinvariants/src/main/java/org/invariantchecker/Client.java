package org.invariantchecker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.*;
import org.invariantgenerator.invariantlanguage.InvariantGenerator;
import org.invariantgenerator.invariantlanguage.PomGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Client {

    static Options options = null;
    static Option queueConfigOption = null;
    static Option invariantOption = null;

    static Option outputOption = null;

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

        outputOption = Option.builder("o")
                .argName("output")
                .longOpt("output")
                .hasArg()
                .desc("output directory")
                .build();

        options.addOption(invariantOption);
        options.addOption(queueConfigOption);
        options.addOption(outputOption);
    }

    public static void main(String[] args) {
        addOptions();

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine line = parser.parse(options, args);
            if (!line.hasOption("invariants")) {
                HelpFormatter formatter = new HelpFormatter();
                System.out.println("Invalid usage of invariant_checker");
                formatter.printHelp("invariant_checker translates invariant files into flink jobs.", options);
                return;
            }

            if (!line.hasOption("output")) {
                HelpFormatter formatter = new HelpFormatter();
                System.out.println("Invalid usage of invariant_checker");
                formatter.printHelp("invariant_checker translates invariant files into flink jobs.", options);
                return;
            }

            var invariantsDir = line.getOptionValue("invariants");
            var queueConfig = Optional.ofNullable(line.getOptionValue("queue-config"));
            var outputDir = line.getOptionValue("output");

            System.out.printf("Invariants directory: %s\nQueue config: %s\n Output directory: %s\n",
                    invariantsDir,
                    queueConfig.orElse("default"),
                    outputDir
                    );

            processInvariants(invariantsDir, queueConfig.get(), outputDir);

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
    private static void processInvariants(String invariantsDir, String queueConfigFilePath, String outputDir) {
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

             var invariantNames = new ArrayList<String>();
            for (var invariantFile : invariantFiles) {
                var invariantQuery =  Files.readString(invariantFile, StandardCharsets.UTF_8);
                var invariantName = invariantFile.getFileName().toString().replace(".inv", "");
                invariantGenerator.generateInvariantFile(outputDir, invariantName, invariantQuery, queueConfig);
                invariantNames.add(invariantName);
            }
            PomGenerator.generatePomFile(outputDir, invariantNames);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
