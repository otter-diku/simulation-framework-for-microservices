package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.api.java.tuple.Tuple2;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatternGenerator {

    private enum ReturnType {
        BOOL, NUMBER, STRING
    }

    private enum OperandType {
        ATOM, QUALIFIED_NAME
    }

    private enum OperatorType {
        EQ, NEQ, GT, GTE, LT, LTE;

        static OperatorType from(String operator) {
            return switch (operator) {
                case "=": yield EQ;
                case "!=": yield NEQ;
                case ">": yield GT;
                case ">=": yield GTE;
                case "<": yield LT;
                case "<=": yield LTE;
                default:
                    throw new RuntimeException();
            };
        }
    }

    private class Operand {
        private ReturnType returnType;
        private OperandType operandType;
        private String value;
        private Optional<Boolean> alreadyExistsInContext;

        private Operand(ReturnType returnType, OperandType operandType, String value) {
            this.returnType = returnType;
            this.operandType = operandType;
            this.value = value;
        }

        private Operand(OperandType operandType, ReturnType returnType, String value, boolean alreadyExistsInContext) {
            this.returnType = returnType;
            this.operandType = operandType;
            this.value = value;
            this.alreadyExistsInContext = Optional.of(alreadyExistsInContext);
        }
    }

    final String quantityRegex = "['0-9a-zA-Z\\._]+";
    final String operatorRegex = "=|!=|>|<|>=|<=";
    final String equalityRegex = String.format("(%s)\\s*(%s)\\s*(%s)", quantityRegex, operatorRegex, quantityRegex);

    private final EventSequence eventSequence;
    private final List<InvariantsParser.TermContext> terms;
    private final Map<String, String> id2Type;
    private final Map<String, Map<String, String>> schemata;
    private Optional<Tuple2<Integer, String>> within;
    private Optional<InvariantsParser.Invariant_clauseContext> onFullMatch;
    private List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPartialMatch;
    private final StringBuilder patternCodeBuilder = new StringBuilder();
    private final StringBuilder fullMatchCodeBuilder = new StringBuilder();
    private final StringBuilder prefixMatchCodeBuilder = new StringBuilder();
    private final Pattern pattern;

    public PatternGenerator(EventSequence eventSequence,
                            List<InvariantsParser.TermContext> terms,
                            Map<String, String> id2Type,
                            Map<String, Map<String, String>> schemata,
                            Optional<Tuple2<Integer, String>> within,
                            Optional<InvariantsParser.Invariant_clauseContext> onFullMatch,
                            List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPartialMatch) {
        this.pattern = Pattern.compile(equalityRegex, Pattern.MULTILINE);
        this.eventSequence = eventSequence;
        this.terms = terms;
        this.id2Type = id2Type;
        this.schemata = schemata;
        this.within = within;
        this.onFullMatch = onFullMatch;
        this.onPartialMatch = onPartialMatch;
    }

    public String generatePatternProcessFunction() {
        fullMatchCodeBuilder.append(String.format(
        """                
            new PatternProcessFunction<Event, String>() {
                @Override
                public void processMatch(
                        Map<String, List<Event>> map,
                        Context context,
                        Collector<String> collector)
                        throws Exception {
        """));

        generateProcessFunctionBody();

        fullMatchCodeBuilder.append(
        """
                }
            }
        )
        """
        );

        return fullMatchCodeBuilder.toString();
    }

    private void generateProcessFunctionBody() {
        if (onFullMatch.isEmpty()) {
            fullMatchCodeBuilder.append("return;");
            return;
        }

        var invariantClause = onFullMatch.get();
        if (invariantClause.BOOL() != null) {
            fullMatchCodeBuilder.append(String.format("if(!%s) { collector.collect(map.toString()); } ",
                    invariantClause.BOOL().getText()));
        }

        invariantClause.where_clause().term();

    }

    public String generatePattern() {

        eventSequence
                .getSequence()
                .forEach(this::addNode);

        within.ifPresent(this::addWithin);
        // onFullMatch.ifPresent(this::addOnFullMatch);

        patternCodeBuilder.append(";");
        return patternCodeBuilder.toString();

        // TODO: case: last event is notfollowedBy
        // a, !b -> WHERE (b.id = a.id) AND (a.price > 42)
        // notFollowedBy("b").IterativeCond(event, context) {
        //   return b.id == a.id
        // }.where(IterativeCond(event, context) {
        //   a = context.getEvents("a")
        //   return a.price > 42;
        // }
        // ==> this should be fine

        // now construct iterative conditions for all terms that do not have
        // a negated event
        //pattern = updateWithFinalIterativeCondition(pattern, eventSequence, terms);
    }

    private void addWithin(Tuple2<Integer, String> tuple) {
        var duration = tuple.f0;
        var unit = switch (tuple.f1) {
            case "milli": yield "milliseconds";
            case "sec": yield "seconds";
            case "min": yield "minutes";
            case "hour": yield "hours";
            default: yield "";
        };

        patternCodeBuilder.append(String.format(".within(Time.%s(%s));", unit, duration));
    }

    private void addNode(SequenceNode node) {
        if (node.position == 0) {
            addBegin(node);
        }
        else if (node.negated) {
            addNotFollowedBy(node);
        }
        else {
            addFollowedByAny(node);
        }

        addQuantifiers(node);

        addSimpleConditionForNode(node, id2Type);

        if (requiresImmediateWhereClause(node)) {
            terms.stream()
                    .filter(term -> getReferencedEventIds(term).contains(node.eventIds.get(0)))
                    .forEach(term -> addWhereClauseFromTerm(node, term));
        }

        // TODO: We could possibly have `else if` here, if we disallow negated and "wildcarded" events to be the last events in the sequence
        if (node.position == eventSequence.getSequence().size() - 1) {
            List<SequenceNode> nodesToExclude = eventSequence.getSequence()
                    .stream()
                    .filter(this::requiresImmediateWhereClause)
                    .collect(Collectors.toList());

            terms.stream()
                    .filter(term -> !doesReferenceNodeToExclude(term, nodesToExclude))
                    .forEach(term -> addWhereClauseFromTerm(node, term));
        }
    }

    private void addQuantifiers(SequenceNode node) {
        if (node.type == SequenceNodeQuantifier.ONE_OR_MORE) {
            patternCodeBuilder.append(".oneOrMore().greedy()");
        } else if (node.type == SequenceNodeQuantifier.ZERO_OR_MORE) {
            patternCodeBuilder.append(".oneOrMore().greedy().optional()");
        }
    }

    private boolean doesReferenceNodeToExclude(InvariantsParser.TermContext term, List<SequenceNode> nodesToExclude) {
        var eventIdsReferencedInTerm = getReferencedEventIds(term);

        return nodesToExclude
                .stream()
                .map(e -> e.eventIds)
                .flatMap(List::stream)
                .anyMatch(eventIdsReferencedInTerm::contains);
    }

    private boolean requiresImmediateWhereClause(SequenceNode node) {
        return node.negated || node.type != SequenceNodeQuantifier.ONCE;
    }

    private void addBegin(SequenceNode node) {
        patternCodeBuilder.append(String.format("Pattern.<Event>begin(\"" + node.getName() + "\")"));
    }

    private void addNotFollowedBy(SequenceNode node) {
        patternCodeBuilder.append(String.format(".notFollowedBy(\"%s\")\n", node.getName()));
    }

    private void addFollowedByAny(SequenceNode node) {
        patternCodeBuilder.append(String.format(".followedByAny(\"%s\")\n", node.getName()));
    }

    private void addSimpleConditionForNode(SequenceNode node, Map<String, String> id2Type) {
        var simpleCondition = node.eventIds.stream()
                .map(eId -> String.format("e.Type.equals(\"%s\")\n", id2Type.get(eId)))
                .collect(Collectors.joining("||"));

        patternCodeBuilder.append(String.format(".where(SimpleCondition.of(e -> %s))\n", simpleCondition));
    }

    private Operand convertToOperand(SequenceNode currentNode, String operand) {
        if (isQualifiedName(operand)) {
            return convertQualifiedNameToOperand(currentNode, operand);
        }

        return convertAtomToOperand(operand);
    }

    private Operand convertQualifiedNameToOperand(SequenceNode currentNode, String operand) {
        // TODO: assumes flat event schema
        var eventId = getEventId(operand);
        var memberId = getMember(operand);
        var returnType = schemata
                .get(eventId)
                .get(memberId);
        var operandType = ReturnType.valueOf(returnType.toUpperCase());
        var alreadyExistsInContext = !currentNode.eventIds.contains(eventId);

        return new Operand(OperandType.QUALIFIED_NAME, operandType, operand, alreadyExistsInContext);
    }

    private Operand convertAtomToOperand(String operand) {
        ReturnType type;
        if (isBooleanAtom(operand)) {
            type = ReturnType.BOOL;
        } else if (isNumberAtom(operand)) {
            type = ReturnType.NUMBER;
        } else if (isStringAtom(operand)) {
            type = ReturnType.STRING;
        } else {
            System.out.println("ERROR: Unknown atom type " + operand);
            return null;
        }
        return new Operand(type, OperandType.ATOM, operand);
    }

    private void addWhereClauseFromTerm(SequenceNode node, InvariantsParser.TermContext term) {
        var termText = term.getText();

        termText = termText
                .replace("AND", "&&")
                .replace("OR", "||");

        // (x.id = y.id)

        final Matcher matcher = pattern.matcher(termText);

        while (matcher.find()) {
            var lhs = convertToOperand(node, matcher.group(1));
            var op = OperatorType.from(matcher.group(2));
            var rhs = convertToOperand(node, matcher.group(3));

            validateOperation(lhs, op, rhs);

            patternCodeBuilder.append(generateCodeFromOperation(lhs, op, rhs));
        }
    }

    private String generateCodeFromOperation(Operand lhs, OperatorType operatorType, Operand rhs) {
        var sb = new StringBuilder();

        /*
         * Operand can be:
         *   - an atom
         *   - a single event already in the context, e.g. SEQ(a, !b, c) -> we are looking for a
         *   - a single OR'ed event already in the context, e.g. SEQ(a, (d | e), !b, c) -> we are looking for d or e
         *   // TODO: maybe we could disallow (a, (e1|e2)) where e1 and e2 have the same type.
         *   - a wildcard event NOT in the context; in this case Flink CEP will not give us the preceding matches of the wildcard in the context.
         *       e.g. (a, b, c+, d) -> we are looking for c at the stage of 'c+'
         * NOT SUPPORTED RIGHT NOW:
         *   - a wildcard event already in the context e.g. SEQ(a, e+, f, !b, c) -> we are looking for e at the stage of '!b'
         */

        var lhsCode = generateCodeFromOperand(lhs, "lhs");
        var rhsCode = generateCodeFromOperand(rhs, "rhs");
        var comparisonCode = generateCodeFromOperator(operatorType, lhs.returnType, "lhs","rhs");

        sb.append(
            String.format(
            """
            .where(
                new IterativeCondition<>() {
                    @Override
                    public boolean filter(Event event, Context<Event> context) throws Exception {
                    %s
                    %s
                    %s
                    }
                })
            """, lhsCode, rhsCode, comparisonCode
            )
        );

        return sb.toString();
    }

    private String generateCodeFromOperator(OperatorType operatorType, ReturnType returnType, String lhs, String rhs) {
        var sb = new StringBuilder();

        var lhsValue = String.format("%s.get()", lhs);
        var rhsValues = String.format("%s.get()", rhs);

        var comparisonCode = switch (operatorType) {
            case EQ: yield returnType == ReturnType.STRING
                    ? String.format("%s.equals(%s)", lhsValue, rhsValues)
                    : String.format("%s == %s", lhsValue, rhsValues);
            case NEQ: yield returnType == ReturnType.STRING
                        ? String.format("!%s.equals(%s)", lhsValue, rhsValues)
                        : String.format("%s != %s", lhsValue, rhsValues);
            case GT: yield String.format("%s > %s", lhsValue, rhsValues);
            case GTE: yield String.format("%s >= %s", lhsValue, rhsValues);
            case LT: yield String.format("%s < %s", lhsValue, rhsValues);
            case LTE: yield String.format("%s <= %s", lhsValue, rhsValues);
        };

        sb.append(
            String.format(
            """
                if (%s.isPresent() && %s.isPresent()) {
                    return %s;
                } else {
                    return false;
                }
            """, lhs, rhs, comparisonCode)
        );

        return sb.toString();
    }

    private String generateCodeFromOperand(Operand operand, String variableName) {
        return switch (operand.operandType) {
            case ATOM:
                yield generateCodeFromAtom(operand, variableName);
            case QUALIFIED_NAME:
                yield generateCodeFromQualifiedName(operand, variableName);
        };
    }

    private static String generateCodeFromAtom(Operand operand, String variableName) {
        return switch (operand.returnType) {
            case STRING:
                yield String.format("var %s = Optional.ofNullable(\"%s\");", variableName, operand.value);
            case BOOL, NUMBER:
                yield String.format("var %s = Optional.of(%s);", variableName, Double.valueOf(operand.value));
        };
    }

    private String generateCodeFromQualifiedName(Operand operand, String variableName) {
        var sb = new StringBuilder();

        var eventId = getEventId(operand.value);
        var nodePosition = eventSequence.getEventPositionById(eventId);
        var node = eventSequence.getSequence().get(nodePosition);

        sb.append(
            String.format(
            """
            Optional<%s> %s;
            try {
            
            """, toJavaType(operand.returnType), variableName)
        );

        if (operand.alreadyExistsInContext.get()) {
            sb.append(
                String.format(
                    """
                        var temp = context.getEventsForPattern("%s")
                                .iterator()
                                .next();
                    """, node.getName())
            );
        } else {
            sb.append("var temp = event;\n");
        }

        sb.append(
            String.format(
                """
                    %s = Optional.of(temp.Content.get("%s")%s);
                } catch (Exception e) {
                    %s = Optional.ofNullable(null);
                }
                
                """, variableName, getMember(operand.value), toJsonDataType(operand.returnType), variableName)
        );

        return sb.toString();
    }

    private static String toJavaType(ReturnType returnType) {
        return switch (returnType) {
            case BOOL: yield "Boolean";
            case NUMBER: yield "Double";
            case STRING: yield "String";
        };
    }

    private static String toJsonDataType(ReturnType returnType) {
        return switch (returnType) {
            case BOOL:
                yield ".asBoolean()";
            case NUMBER:
                yield ".asDouble()";
            case STRING:
                yield ".asText()";
        };
    }

    private void validateOperation(Operand lhs, OperatorType op, Operand rhs) {
        if (!lhs.returnType.equals(rhs.returnType)) {
            throw new RuntimeException("ERROR: Lhs type does not equal Rhs type.");
        }

        if (lhs.operandType == OperandType.ATOM && lhs.operandType == rhs.operandType) {
            throw new RuntimeException("ERROR: Comparison of atoms not allowed");
        }

        if ((lhs.returnType == ReturnType.BOOL || lhs.returnType == ReturnType.STRING)
                && List.of(OperatorType.LT, OperatorType.LTE, OperatorType.GT, OperatorType.GTE).contains(op)) {
            throw new RuntimeException("ERROR: Using numeric operator on incompatible type.");
        }
    }

    private Boolean isBooleanAtom(String atom) {
        return (atom.equals("true")
                || (atom.equals("false")));
    }
    private Boolean isNumberAtom(String atom) {
        return (atom.charAt(0) >= '0' && atom.charAt(0) <= '9')
                || (atom.charAt(0) == '.');
    }
    private Boolean isStringAtom(String atom) {
        return atom.startsWith("'") && atom.endsWith("'");
    }
    private Boolean isQualifiedName(String string) {
        var isAtom = isBooleanAtom(string)
                || isNumberAtom(string)
                || isStringAtom(string);
        return !isAtom;
    }

    private String getEventId(String qualifiedName) {
        return qualifiedName.split("\\.")[0];
    }

    private String getMember(String qualifiedName) {
        return qualifiedName.split("\\.")[1];
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
}
