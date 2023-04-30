package org.myorg.flinkinvariants.invariantlanguage;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.xpath.XPath;
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

        private List<InvariantsParser.TermContext> termContexts = new ArrayList<>();

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
        public void enterWhere_clause(InvariantsParser.Where_clauseContext ctx) {
            // TODO: validate that conditions contain only valid event IDs
            var terms = ctx.term();
            for (var term : terms) {

                // 1. go inside each term and see if it references the negated event
                // 1a. if yes, make sure that the term does not reference any event ID that is not seen before the negated event
                // 2. save/serialize/whatever the term to use it later
                if (validateTerm(term)) {
                    termContexts.add(term);
                }

            }
        }

        private boolean validateTerm(InvariantsParser.TermContext term) {
            var children = term.children;

            return false;
        }

        private Set<String> referencesNegatedEvent(InvariantsParser.TermContext term) {
            for (var child : term.children.stream().filter(c -> c instanceof InvariantsParser.TermContext).collect(Collectors.toList())) {

                var childTerm = (InvariantsParser.TermContext)child;

                if (childTerm.term() != null) {
                    return childTerm.term().stream().map(this::referencesNegatedEvent).findAny().isPresent();
                }

                var lhs = childTerm.equality().quantity(0);
                var rhs = childTerm.equality().quantity(1);

                // TODO:
                // return a list of negated event IDs being referenced
                //      - if more than 1 negated event ID is being referenced, signal error somehow
                // if a single negated event ID is being referenced, check all the other events being referenced in the term
                //      - if any of the other events appears after the negated event, signal error somehow
            }

            return false;
        }

        private Optional<String> getReferencedNegatedEvents(InvariantsParser.QuantityContext quantity) {
            if (quantity.atom() != null)
                return Optional.empty();

            // TODO: we can't do this on each call
            List<String> negatedEventsInSequence = sequence
                    .stream()
                    .filter(n -> n.Negated)
                    .map(n -> n.EventIds.get(0) /* assuming only 1 event ID for negated sequence node*/)
                    .collect(Collectors.toList());

            var qualifiedNamePrefix = quantity.qualifiedName().getText().split("\\.")[0];
            if (negatedEventsInSequence.contains(qualifiedNamePrefix)) {
                return Optional.ofNullable(qualifiedNamePrefix);
            }

            return Optional.empty();
        }

        @Override
        public void enterEvent(InvariantsParser.EventContext ctx) {
            // TODO: validate that event sequence contains only valid event IDs
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
