package org.invariantchecker;

import org.apache.commons.cli.*;

import java.util.Optional;

public class Client {

    static Options options = null;
    static Option queueConfigOption = null;
    static Option invariantOption = null;

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
            if (!line.hasOption("invariant")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Invalid usage of invariant_checker", options);
            }

            var invariantsDir = line.getParsedOptionValue("invariants");
            var queueConfig = Optional.ofNullable(line.getParsedOptionValue("queue-config"));

            System.out.printf("Invariants directory: %s\nQueue config: %s",
                    invariantsDir,
                    queueConfig.orElse("default"));

        }
        catch (ParseException exception) {
            System.err.println("Parsing failed. Reason: " + exception.getMessage());
        }
    }
}
