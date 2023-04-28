package org.myorg.flinkinvariants.invariantlanguage;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.myorg.invariants.parser.InvariantsBaseListener;
import org.myorg.invariants.parser.InvariantsLexer;
import org.myorg.invariants.parser.InvariantsParser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class InvariantQueryTranslator {

    public static class InvariantLanguage2CEPListener extends InvariantsBaseListener {
        public String invariantName;
        public String outputFile;
        public Boolean firstEvent = true;
        public String lastEvent;
        public int equalityNum = 0;
        public int eventDefinitionNum = 0;
        public List<String> equalities = new ArrayList<>();
        Map<String, String> substitutions = new HashMap<>();

        public StringBuilder invariantBuilder = new StringBuilder();
        public StringBuilder streamBuilder = new StringBuilder();

        public Map<String, String> eventId2EventName = new HashMap<>();

        public InvariantLanguage2CEPListener(String invariantName, String outputFile) {
            this.outputFile = outputFile;
            this.invariantName = invariantName;
        }

        String templateFirstEvent =
                """
                public static Pattern<Event, ?> invariant = Pattern.<Event>begin("IDENTIFIER")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(Event event) throws Exception {
                        return event.Type.equals("IDENTIFIER");
                    }
                })""";

        String templateSubsequentEvent =
                """
            .notFollowedBy("IDENTIFIER")
            .where(new SimpleCondition<Event>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("IDENTIFIER");
                }
            })""";

        @Override
        public void enterEventDefinition(InvariantsParser.EventDefinitionContext ctx) {
            var topic = ctx.topic().IDENTIFIER().toString();

            streamBuilder
                    .append("var streamSource")
                    .append(eventDefinitionNum)
                    .append(" = KafkaReader.GetEventDataStreamSource(env, \"")
                    .append(topic)
                    .append("\", groupId);\n");
            eventDefinitionNum++;
            super.enterEventDefinition(ctx);
        }

//        @Override
//        public void enterEvent(InvariantsParser.EventContext ctx) {
//            lastEvent = ctx.eventId().IDENTIFIER().toString();
//            eventId2EventName.put(
//                    ctx.eventId().IDENTIFIER().toString(),
//                    ctx.eventSchema().IDENTIFIER().toString());
//
//            if (firstEvent) {
//                invariantBuilder.append(
//                        templateFirstEvent.replace(
//                                "IDENTIFIER", ctx.eventSchema().IDENTIFIER().toString()));
//                firstEvent = false;
//                return;
//            }
//            invariantBuilder.append(
//                    templateSubsequentEvent.replace(
//                            "IDENTIFIER", ctx.eventSchema().IDENTIFIER().toString()));
//            super.enterEvent(ctx);
//        }

        @Override
        public void enterWhere_clause(InvariantsParser.Where_clauseContext ctx) {
            String startIterativeCondition =
                    """
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(Event event, Context<Event> context) throws Exception {
                """;
            invariantBuilder.append(startIterativeCondition);
        }

        @Override
        public void exitWhere_clause(InvariantsParser.Where_clauseContext ctx) {

            var operators = ctx.OP();
            invariantBuilder.append("return ");

            var it = equalities.iterator();
            for (int i = 0; i < equalities.size() - 1; i++) {
                invariantBuilder.append(it.next());

                var op = operators.get(i);
                String javaOp;
                switch (op.toString()) {
                    case "AND":
                        javaOp = " && ";
                        break;
                    case "OR":
                        javaOp = " || ";
                        break;
                    default:
                        javaOp = "Unexpected Operator";
                        break;
                }
                invariantBuilder.append(javaOp);
            }
            invariantBuilder.append(it.next()).append(";");

            String endIterativeCondition = """
                  }
              })""";
            invariantBuilder.append(endIterativeCondition);
        }

        @Override
        public void enterEquality(InvariantsParser.EqualityContext ctx) {
            // TODO: have to handle last event in event sequence special (is event in iterative
            // condition
            StringBuilder equalityBuilder = new StringBuilder();
            String templateLastEvent =
                    """
                    event.Content.get("ATTR").toString()
                    """;
            String templateOthers =
                    """
                     context.getEventsForPattern("EVENT").iterator().next().Content.get("ATTR").toString()
                     """;

            var quantity1Code =
                    createEqualityCode(ctx.quantity(0), templateLastEvent, templateOthers);
            var quantity2Code =
                    createEqualityCode(ctx.quantity(1), templateLastEvent, templateOthers);

            switch (ctx.EQ_OP().toString()) {
                case "=":
                    equalityBuilder
                            .append(quantity1Code)
                            .append(".equals(")
                            .append(quantity2Code)
                            .append(")");
                    break;
                case "!=":
                    equalityBuilder
                            .append('!')
                            .append(quantity1Code)
                            .append(".equals(")
                            .append(quantity2Code)
                            .append(")");
                    break;
                case "<":
                    equalityBuilder.append(quantity1Code).append(" < ").append(quantity2Code);
                    break;
                case ">":
                    equalityBuilder.append(quantity1Code).append(" >").append(quantity2Code);
                    break;
                case "<=":
                    equalityBuilder.append(quantity1Code).append(" <= ").append(quantity2Code);
                    break;
                case ">=":
                    equalityBuilder.append(quantity1Code).append(" >= ").append(quantity2Code);
                    break;
                default:
                    equalityBuilder
                            .append("Unexpected operator: ")
                            .append(ctx.EQ_OP().toString());
                    break;
            }

            equalityNum++;
            equalities.add(equalityBuilder.toString());
        }

        private String createEqualityCode(
                InvariantsParser.QuantityContext quantity,
                String templateLastEvent,
                String templateOthers) {
            if (quantity.qualifiedName() != null) {
                var eventId = quantity.qualifiedName().IDENTIFIER(0).toString();
                var attribute = quantity.qualifiedName().IDENTIFIER(1).toString();
                if (eventId.equals(lastEvent)) {
                    return templateLastEvent.replace("ATTR", attribute);
                } else {
                    return templateOthers.replace("EVENT", eventId).replace("ATTR", attribute);
                }
            } else {
                if (quantity.atom().INT() != null) {
                    return quantity.atom().INT().toString();
                } else {
                    return "\"" + quantity.atom().BOOL().toString() + "\"";
                }
            }
        }

        @Override
        public void enterTime(InvariantsParser.TimeContext ctx) {
            int duration = Integer.parseInt(ctx.INT().toString());
            String unit = ctx.TIME().toString();

            String within;
            switch (unit) {
                case "milli":
                    within = ".within(Time.milliseconds(" + duration + "));";
                    break;
                case "sec":
                    within = ".within(Time.seconds(" + duration + "));";
                    break;
                case "min":
                    within = ".within(Time.min(" + duration + "));";
                    break;
                case "hour":
                    within = ".within(Time.hour(" + duration + "));";
                    break;
                default:
                    within = "Unexpected time unit";
                    break;
            }
            invariantBuilder.append(within);
        }

        @Override
        public void exitInvariant(InvariantsParser.InvariantContext ctx) {
            substitutions.put(
                    "package org.myorg.flinkinvariants.invariantlanguage;",
                    "package org.myorg.flinkinvariants.invariantcheckers;");
            substitutions.put("// STREAMS", streamBuilder.toString());
            substitutions.put("DataStream<Event> inputStream = null;", buildStreamCode());
            substitutions.put(
                    "public static Pattern<Event, ?> invariant;", invariantBuilder.toString());
            substitutions.put(
                    "public class InvariantTemplate {", "public class " + invariantName + " {");
            createInvariantFile(outputFile, substitutions);
        }

        private String buildStreamCode() {
            StringBuilder inputStreamBuilder = new StringBuilder();
            inputStreamBuilder.append("DataStream<Event> inputStream = streamSource0");

            for (int i = 1; i < eventDefinitionNum; i++) {
                if (i == 1) {
                    inputStreamBuilder.append(".union(streamSource1");
                } else {
                    inputStreamBuilder.append(", streamSource").append(i);
                }
            }

            if (eventDefinitionNum == 1) {
                inputStreamBuilder.append(";");
            } else {
                inputStreamBuilder.append(");");
            }
            return inputStreamBuilder.toString();
        }
    }

    public void translateQuery(String query, String invariantName, String outputFile) {
        ANTLRInputStream input = new ANTLRInputStream(query);
        // create a lexer that feeds off of input CharStream
        InvariantsLexer lexer = new InvariantsLexer(input);

        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        // create a parser that feeds off the tokens buffer
        InvariantsParser parser = new InvariantsParser(tokens);
        ParseTree tree = parser.invariant(); // begin parsing at init rule

        ParseTreeWalker walker = new ParseTreeWalker();
        InvariantLanguage2CEPListener translator =
                new InvariantLanguage2CEPListener(invariantName, outputFile);

        walker.walk(translator, tree);
    }


    private static void createInvariantFile(String outputFile, Map<String, String> substitions) {
        String inputFile =
                "src/main/java/org/myorg/flinkinvariants/invariantlanguage/InvariantTemplate.java";

        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            String line = reader.readLine();
            while (line != null) {
                for (var key : substitions.keySet()) {
                    if (line.contains(key)) {
                        line = substitions.get(key);
                    }
                }
                writer.write(line);
                writer.newLine();
                line = reader.readLine();
            }
            reader.close();
            writer.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
