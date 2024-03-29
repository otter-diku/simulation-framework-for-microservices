package org.invariantgenerator.invariantlanguage;

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

        private final Map<String, Map<String, String>> schemata = new HashMap<>();

        private final EventSequence sequence = new EventSequence();
        private final List<InvariantsParser.TermContext> whereClauseTerms = new ArrayList<>();

        private Optional<InvariantsParser.Invariant_clauseContext> onFullMatch = Optional.empty();
        private Optional<Tuple2<Integer, String>> within = Optional.empty();
        private List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPrefixMatch = new ArrayList<>();

        private boolean semanticAnalysisFailed = false;

        @Override
        public void enterEventDefinition(InvariantsParser.EventDefinitionContext ctx) {
            var topic = ctx.topic().IDENTIFIER().toString();
            var type = ctx.eventType().IDENTIFIER().toString();
            var id = ctx.eventId().IDENTIFIER().toString();

            var schema = ctx.schema().schemaMember()
                    .stream()
                    .collect(Collectors.toMap(sm -> sm.IDENTIFIER().getText(), sm -> sm.memberType().getText()));

            topics.add(topic);
            relevantEventTypes.add(type);
            id2Type.put(id, type);
            schemata.put(ctx.eventId().getText(), schema);
        }

        @Override
        public void enterWhere_clause(InvariantsParser.Where_clauseContext ctx) {
            var terms = ctx.term();

            for (var term : terms) {
                if (validateTerm(term)) {
                    whereClauseTerms.add(term);
                }
                else {
                    semanticAnalysisFailed = true;
                }
            }
        }

        @Override
        public void enterInvariant_clause(InvariantsParser.Invariant_clauseContext ctx) {
            var terms = ctx.term();

            for (var term : terms) {
                if (!validateTerm(term)) {
                    semanticAnalysisFailed = true;
                }
            }
        }

        @Override
        public void enterOn_full_match(InvariantsParser.On_full_matchContext ctx) {
            onFullMatch = Optional.ofNullable(ctx.invariant_clause());
        }

        @Override
        public void enterOn_prefix_match(InvariantsParser.On_prefix_matchContext ctx) {
            onPrefixMatch.add(new Tuple2<>(ctx.prefix(), ctx.invariant_clause()));
        }

        @Override
        public void enterTime(InvariantsParser.TimeContext ctx) {
            within = Optional.of(new Tuple2<>(Integer.parseInt(ctx.INT().getText()), ctx.TIME().getText()));
        }

        private boolean validateTerm(InvariantsParser.TermContext term) {
            var referencedEventIds = getReferencedEventIds(term);

            if (!referencedEventIds.stream().allMatch(id2Type::containsKey)) {
                return false;
            }
            var negatedEventsInSequence = sequence
                    .getSequence()
                    .stream()
                    .filter(n -> n.negated)
                    .map(n -> n.eventIds.get(0) /* assuming only 1 event ID for negated sequence node*/)
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
            var sequenceNode = createSequenceNode(ctx, sequence.getSequence().size());
            if ((!sequenceNode.eventIds.stream().allMatch(id2Type::containsKey))
                    || (!sequence.addNode(sequenceNode))) {
                semanticAnalysisFailed = true;
            }
        }

        private SequenceNode createSequenceNode(InvariantsParser.EventContext eventContext, int currentSequenceSize) {
            var isOrOperator = eventContext.orOperator() != null;
            SequenceNode.SequenceNodeBuilder sequenceNodeBuilder;

            Optional<String> regexOp;
            if (isOrOperator) {
                var eventIds = eventContext.orOperator()
                        .eventId()
                        .stream()
                        .map(eventIdContext -> eventIdContext.IDENTIFIER().getText())
                        .collect(Collectors.toList());
                sequenceNodeBuilder = new SequenceNode.SequenceNodeBuilder(eventIds);
                sequenceNodeBuilder.setNeg(false);
                sequenceNodeBuilder.setPosition(currentSequenceSize);

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
                sequenceNodeBuilder.setPosition(currentSequenceSize);

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
            if (!validateFinishedSequence()) {
                semanticAnalysisFailed = true;
                return;
            }

            // sequence.getSequence().forEach(System.out::println);
        }


        private boolean validateFinishedSequence() {
            SequenceNode lastSequenceNode = getLastSequenceNode();
            return lastSequenceNode.type == SequenceNodeQuantifier.ONCE;
        }

        private SequenceNode getLastSequenceNode() {
            var sequenceLength = sequence.getSequence().size();
            return sequence.getSequence().get(sequenceLength - 1);
        }


        public List<InvariantsParser.TermContext> getTerms() {
            return whereClauseTerms;
        }

        private boolean validateFinishedInvariant() {
            var lastNode = getLastSequenceNode();

            // If last node is negated we require the WITHIN to be specified
            if (lastNode.negated) {
                return within.isPresent();
            }

            return true;
        }
    }

    public TranslationResult translateQuery(String query) {
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

        return new TranslationResult(
                parser.getNumberOfSyntaxErrors(),
                translator.semanticAnalysisFailed && translator.validateFinishedInvariant(),
                translator.topics,
                translator.relevantEventTypes,
                translator.id2Type,
                translator.schemata,
                translator.sequence,
                translator.whereClauseTerms,
                translator.within,
                translator.onFullMatch,
                translator.onPrefixMatch);
    }

}
