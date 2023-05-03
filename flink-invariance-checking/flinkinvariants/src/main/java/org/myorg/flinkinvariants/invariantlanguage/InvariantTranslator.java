package org.myorg.flinkinvariants.invariantlanguage;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.flink.api.java.tuple.Tuple2;
import org.myorg.invariants.parser.InvariantsBaseListener;
import org.myorg.invariants.parser.InvariantsLexer;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class InvariantTranslator {

    public static class InvariantLanguage2CEPListener extends InvariantsBaseListener {
        private final Set<String> topics = new HashSet<>();
        private final Set<String> relevantEventTypes = new HashSet<>();
        private final Map<String, String> id2Type = new HashMap<>();

        private final Map<String, List<Tuple2<String, String>>> schemata = new HashMap<>();

        private final EventSequence sequence = new EventSequence();
        private final List<InvariantsParser.TermContext> termContexts = new ArrayList<>();

        private boolean semanticAnalysisFailed = false;

        @Override
        public void enterEventDefinition(InvariantsParser.EventDefinitionContext ctx) {
            var topic = ctx.topic().IDENTIFIER().toString();
            var type = ctx.eventType().IDENTIFIER().toString();
            var id = ctx.eventId().IDENTIFIER().toString();

            var schema = ctx.schema().schemaMember()
                    .stream()
                    .map(sm -> new Tuple2<>(sm.IDENTIFIER().getText(), sm.memberType().getText()))
                    .collect(Collectors.toList());

            topics.add(topic);
            relevantEventTypes.add(type);
            id2Type.put(id, type);
            schemata.put(ctx.eventId().getText(), schema);
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
                else {
                    semanticAnalysisFailed = true;
                }
            }
        }

        private boolean validateTerm(InvariantsParser.TermContext term) {
            var referencedEventIds = getReferencedEventIds(term);

            var negatedEventsInSequence = sequence
                    .getSequence()
                    .stream()
                    .filter(n -> n.Negated)
                    .map(n -> n.EventIds.get(0) /* assuming only 1 event ID for negated sequence node*/)
                    .collect(Collectors.toList());

            var grouping = referencedEventIds.stream()
                    .collect(groupingBy(negatedEventsInSequence::contains));

            var negatedEventIds = grouping.getOrDefault(true, List.of());
            var nonNegatedEventIds = grouping.getOrDefault(false, List.of());

            return switch (negatedEventIds.size()) {
                case 0:
                    yield true;
                case 1:
                    yield validateOrderingOfNegatedEventIds(negatedEventIds.get(0), nonNegatedEventIds);
                default:
                    yield false;
            };
        }

        private boolean validateOrderingOfNegatedEventIds(String negatedEventId, List<String> nonNegatedEventIds) {
            var negatedEventPosition = sequence.getEventPositionById(negatedEventId);

            if (negatedEventPosition == -1) {
                return false; // Something went wrong
            }

            for (var nonNegatedEventId : nonNegatedEventIds) {
                var position = sequence.getEventPositionById(nonNegatedEventId);
                if (position == -1) {
                    return false; // Something went wrong
                }

                if (position >= negatedEventPosition) {
                    return false;
                }
            }

            return true;
        }

        private Set<String> getReferencedEventIds(InvariantsParser.TermContext term) {
            var subTerms = term.children.stream()
                    .filter(c -> c instanceof InvariantsParser.TermContext)
                    .map(c -> (InvariantsParser.TermContext) c)
                    .collect(Collectors.toList());

            if ((long) subTerms.size() > 0) {
                return subTerms
                        .stream()
                        .map(this::getReferencedEventIds)
                        .reduce(new HashSet<>(), (a, e) -> {
                            a.addAll(e);
                            return a;
                        });
            }

            // If we are here, we know that the term contains only a single equality
            var result = new HashSet<String>();

            var ref1 = getReferencedEventId(term.equality().quantity(0));
            ref1.ifPresent(result::add);

            var ref2 = getReferencedEventId(term.equality().quantity(1));
            ref2.ifPresent(result::add);

            return result;
        }

        private Optional<String> getReferencedEventId(InvariantsParser.QuantityContext quantity) {
            if (quantity.atom() != null)
                return Optional.empty();

            var qualifiedNamePrefix = quantity
                    .qualifiedName()
                    .getText()
                    .split("\\.")[0];

            return Optional.ofNullable(qualifiedNamePrefix);
        }

        @Override
        public void enterEvent(InvariantsParser.EventContext ctx) {
            // TODO: validate that event sequence contains only valid event IDs
            var sequenceNode = createSequenceNode(ctx);
            if (!sequence.addNode(sequenceNode)) {
                semanticAnalysisFailed = true;
            }
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

            sequence.getSequence().forEach(System.out::println);
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

        public List<InvariantsParser.TermContext> getTerms() {
            return termContexts;
        }
    }

    public TranslationResult translateQuery(String query, String invariantName, String outputFile) {
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
        return new TranslationResult(parser.getNumberOfSyntaxErrors(), translator.semanticAnalysisFailed);
    }

    public List<InvariantsParser.TermContext> getTermsFromQuery(String query) {
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
        return translator.getTerms();
    }
}
