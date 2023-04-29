package org.myorg.flinkinvariants.invariantlanguage;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.myorg.invariants.parser.InvariantsLexer;
import org.myorg.invariants.parser.InvariantsParser;
import org.myorg.invariants.parser.InvariantsBaseListener;

import java.util.*;
import java.util.stream.Collectors;

public class InvariantTranslator {

    public static class InvariantLanguage2CEPListener extends InvariantsBaseListener {
        private Set<String> topics = new HashSet<>();
        private Set<String> relevantEventTypes = new HashSet<>();
        private Map<String, String> id2Type = new HashMap<>();

        private List<SequenceNode> sequence = new ArrayList<>();

        @Override
        public void enterEventDefinition(InvariantsParser.EventDefinitionContext ctx) {
            var topic = ctx.topic().IDENTIFIER().toString();
            var type = ctx.eventType().IDENTIFIER().toString();
            var id = ctx.eventId().IDENTIFIER().toString();

            topics.add(topic);
            relevantEventTypes.add(type);
            id2Type.put(id, type);
        }

        @Override
        public void enterEvent(InvariantsParser.EventContext ctx) {
            var sequenceNode = createSequenceNode(ctx);
            sequence.add(sequenceNode);

        }

        private SequenceNode createSequenceNode(InvariantsParser.EventContext eventContext) {
            var isOrOperator = eventContext.orOperator() != null;
            SequenceNode.SequenceNodeBuilder sequenceNodeBuilder;

            Optional<String> regexOp;
            if (isOrOperator) {
                var eventIds = eventContext.orOperator().eventId().stream().map(eventIdContext -> eventIdContext.IDENTIFIER().getText()).collect(Collectors.toList());
                sequenceNodeBuilder = new SequenceNode.SequenceNodeBuilder(eventIds);
                sequenceNodeBuilder.setNeg(false);

                regexOp = Optional.ofNullable(eventContext.orOperator().regexOp())
                        .map(RuleContext::getText);
            } else {
                var eventAtomContext = eventContext.eventAtom();
                var isNegEvent = eventAtomContext.negEvent() != null;

                List<String> eventIds = new ArrayList<>();
                if (isNegEvent) {
                    eventIds.add(eventAtomContext.negEvent().eventId().IDENTIFIER().toString());
                } else {
                    eventIds.add(eventAtomContext.eventId().IDENTIFIER().toString());
                }
                sequenceNodeBuilder = new SequenceNode.SequenceNodeBuilder(eventIds);
                sequenceNodeBuilder.setNeg(isNegEvent);
                regexOp = Optional.ofNullable(eventContext.eventAtom().regexOp())
                        .map(RuleContext::getText);
            }


            if (regexOp.isPresent()) {
                switch (regexOp.get()) {
                    case "+": sequenceNodeBuilder.setType(SequenceNodeQuantifier.ONE_OR_MORE);
                        break;
                    case "*": sequenceNodeBuilder.setType(SequenceNodeQuantifier.ZERO_OR_MORE);
                        break;
                }
            } else {
                sequenceNodeBuilder.setType(SequenceNodeQuantifier.ONCE);
            }

            return sequenceNodeBuilder.build();
        }

        @Override
        public void exitInvariant(InvariantsParser.InvariantContext ctx) {
            var streams = generateDataStreamCode(topics);
            System.out.println(streams);
            var filterOperator = generateStreamFilter(relevantEventTypes);
            System.out.println(filterOperator);

            sequence.forEach(System.out::println);
        }

        private String generateDataStreamCode(Set<String> topics) {
            var datastreamBuilder = new StringBuilder();
            var topicNum = 0;
            for (var topic : topics) {
                datastreamBuilder
                        .append("var streamSource")
                        .append(topicNum)
                        .append(" = KafkaReader.GetEventDataStreamSource(env, \"")
                        .append(topic)
                        .append("\", groupId);\n");
                topicNum++;
            }
            return  datastreamBuilder.toString();
        }

        private String generateStreamFilter(Set<String> relevantEventTypes) {
            var filterBuilder = new StringBuilder();
            filterBuilder.append(
                "var filteredSource = streamSource.filter(event -> false "
            );

            for (var type : relevantEventTypes) {
                filterBuilder.append(
                    String.format("|| event.Type.equals(\"%s\") ", type)
                );
            }

            filterBuilder.append(").setParallelism(1);");
            return filterBuilder.toString();
        }
    }

    public int translateQuery(String query, String invariantName, String outputFile) {
        ANTLRInputStream input = new ANTLRInputStream(query);
        // create a lexer that feeds off of input CharStream
        InvariantsLexer lexer = new InvariantsLexer(input);

        // create a buffer of tokens pulled from the lexer
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        // create a parser that feeds off the tokens buffer
        InvariantsParser parser = new InvariantsParser(tokens);
        ParseTree tree = parser.invariant(); // begin parsing at init rule

        ParseTreeWalker walker = new ParseTreeWalker();
        InvariantLanguage2CEPListener translator = new InvariantLanguage2CEPListener();

        walker.walk(translator, tree);
        return parser.getNumberOfSyntaxErrors();
    }

}
