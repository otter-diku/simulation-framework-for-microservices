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

        private Operand(ReturnType returnType, OperandType operandType, String value) {
            this.returnType = returnType;
            this.operandType = operandType;
            this.value = value;
        }
    }

    final String quantityRegex = "['0-9a-zA-Z\\._]+";
    final String operatorRegex = "=|!=|>|<|>=|<=";
    final String equalityRegex = String.format("(%s)\\s*(%s)\\s*(%s)", quantityRegex, operatorRegex, quantityRegex);

    private final EventSequence eventSequence;
    private final List<InvariantsParser.TermContext> whereClauseTerms;

    private final List<InvariantsParser.TermContext> fullMatchTerms;

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
                            List<InvariantsParser.TermContext> whereClauseTerms,
                            List<InvariantsParser.TermContext> fullMatchTerms,
                            Map<String, String> id2Type,
                            Map<String, Map<String, String>> schemata,
                            Optional<Tuple2<Integer, String>> within,
                            Optional<InvariantsParser.Invariant_clauseContext> onFullMatch,
                            List<Tuple2<InvariantsParser.PrefixContext, InvariantsParser.Invariant_clauseContext>> onPartialMatch) {
        this.pattern = Pattern.compile(equalityRegex, Pattern.MULTILINE);
        this.eventSequence = eventSequence;
        this.whereClauseTerms = whereClauseTerms;
        this.fullMatchTerms = fullMatchTerms;
        this.id2Type = id2Type;
        this.schemata = schemata;
        this.within = within;
        this.onFullMatch = onFullMatch;
        this.onPartialMatch = onPartialMatch;
    }



    public String generatePattern() {

        var toDefineLater = eventSequence
                .getSequence()
                .stream()
                .map(this::addNode)
                .filter(l -> l.size() > 0)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        within.ifPresent(this::addWithin);
        // onFullMatch.ifPresent(this::addOnFullMatch);

        patternCodeBuilder.append(";\n");

        toDefineLater.forEach(s -> patternCodeBuilder.append(s).append("\n"));

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

    public String generateInvariants() {
        var sb = new StringBuilder();

        String processTimeOutBody = getProccessTimeOutBody();
        var processFunctionBody = getProcessFunctionBody();


        sb.append(String.format("""
        private static final OutputTag<String> outputTag = new OutputTag<>("timeoutMatch") {};
        
        public static class MyPatternProcessFunction
                extends PatternProcessFunction<Event, String>
                implements TimedOutPartialMatchHandler<Event> {
            %s
            %s
        }
        """, processFunctionBody.f0,
             processTimeOutBody));

        processFunctionBody.f1.forEach(functionBody -> sb.append(functionBody).append("\n"));
        return sb.toString();
    }

    private String getProccessTimeOutBody() {
        return
                """
                @Override
                public void processTimedOutMatch(Map<String, List<Event>> map, Context context) {
                }
                """;
    }

    private Tuple2<String, List<String>> getProcessFunctionBody() {
        var temp =
                  """
                  @Override
                  public void processMatch(Map<String, List<Event>> map, Context context, Collector<String> collector) {
                    %s
                  }
                """;
        if (onFullMatch.isEmpty()) {
            return new Tuple2<>(String.format(temp, "return;"), List.of());
        }

        var invariantClause = onFullMatch.get();
        if (invariantClause.BOOL() != null) {
            if (invariantClause.BOOL().getText().equals("false")) {
                return new Tuple2<>(String.format(temp, "collector.collect(map.toString());"), List.of());
            }
            return new Tuple2<>(String.format(temp, "return;"), List.of());
        }

        var translatedTerms = fullMatchTerms.stream().map(t -> translateTermFromInvariant(t)).collect(Collectors.toList());
        var fullInvariantExpression = translatedTerms.stream().map(tuple -> "(" + tuple.f0 + ")").collect(Collectors.joining(" && "));

        var toDefineLater = translatedTerms.stream().map(tuple -> tuple.f1).flatMap(List::stream).collect(Collectors.toList());
        var processMatch = String.format(temp, String.format("if(!(%s)) { collector.collect(map.toString()); }",
                fullInvariantExpression));
        return new Tuple2<>(processMatch, toDefineLater);
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

    private ArrayList<String> addNode(SequenceNode node) {

        var toDefineLater = new ArrayList<String>();

        if (node.position == 0) {
            addBegin(node);
        }
        else if (node.negated) {
            addNotFollowedBy(node);
        }
        else if (node.type == SequenceNodeQuantifier.ONCE){
            addFollowedByAny(node);
        } else {
            addFollowedBy(node);
        }

        addQuantifiers(node);

        addSimpleConditionForNode(node, id2Type);

        if (requiresImmediateWhereClause(node)) {
            var functions = whereClauseTerms.stream()
                    .filter(term -> getReferencedEventIds(term).contains(node.eventIds.get(0)))
                    .map(term -> translateTermFromWhereClause(node, term))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            toDefineLater.addAll(functions);
        }

        // TODO: We could possibly have `else if` here, if we disallow negated and "wildcarded" events to be the last events in the sequence
        if (node.position == eventSequence.getSequence().size() - 1) {
            List<SequenceNode> nodesToExclude = eventSequence.getSequence()
                    .stream()
                    .filter(this::requiresImmediateWhereClause)
                    .collect(Collectors.toList());

            var functions = whereClauseTerms.stream()
                    .filter(term -> !doesReferenceNodeToExclude(term, nodesToExclude))
                    .map(term -> translateTermFromWhereClause(node, term))
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            toDefineLater.addAll(functions);
        }

        return toDefineLater;
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

    private void addFollowedBy(SequenceNode node) {
        patternCodeBuilder.append(String.format(".followedBy(\"%s\")\n", node.getName()));
    }

    private void addSimpleConditionForNode(SequenceNode node, Map<String, String> id2Type) {
        var simpleCondition = node.eventIds.stream()
                .map(eId -> String.format("e.Type.equals(\"%s\")\n", id2Type.get(eId)))
                .collect(Collectors.joining("||"));

        patternCodeBuilder.append(String.format(".where(SimpleCondition.of(e -> %s))\n", simpleCondition));
    }

    private Operand convertToOperand(String operand) {
        if (isQualifiedName(operand)) {
            return convertQualifiedNameToOperand(operand);
        }

        return convertAtomToOperand(operand);
    }

    private Operand convertQualifiedNameToOperand(String operand) {
        // TODO: assumes flat event schema
        var eventId = getEventId(operand);
        var memberId = getMember(operand);
        var returnTypeString = schemata
                .get(eventId)
                .get(memberId);
        var returnType = ReturnType.valueOf(returnTypeString.toUpperCase());

        return new Operand(returnType, OperandType.QUALIFIED_NAME, operand);
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

    private Tuple2<String, List<String>> translateTermFromInvariant(InvariantsParser.TermContext term) {
        var termText = term.getText();

        termText = termText
                .replace("AND", "&&")
                .replace("OR", "||");

        var resultText = termText;
        final Matcher matcher = pattern.matcher(termText);

        var functions = new ArrayList<String>();

        while (matcher.find()) {
            // (a=b) ==> group0
            var lhs = convertToOperand(matcher.group(1));
            var op = OperatorType.from(matcher.group(2));
            var rhs = convertToOperand(matcher.group(3));

            validateOperation(lhs, op, rhs);

            var tuple = generateCodeFromOperationForInvariant(lhs, op, rhs);
            functions.add(tuple.f1);

            resultText = resultText.replace(matcher.group(0), tuple.f0 + "(map)");
        }
        return new Tuple2<>(resultText, functions);
    }

    private Tuple2<String, String> generateCodeFromOperationForInvariant(Operand lhs, OperatorType op, Operand rhs) {
        var lhsCode = generateCodeFromOperandForInvariant(lhs, "lhs");
        var rhsCode = generateCodeFromOperandForInvariant(rhs, "rhs");
        var comparisonCode = generateCodeFromOperatorForInvariant(op, lhs.returnType, "lhs","rhs");

        var functionName = "__" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var functionBody = String.format(
                """
                static boolean %s(Map<String, List<Event>> map) {
                        %s
                        %s
                        %s
                };
                """, functionName, lhsCode, rhsCode, comparisonCode
        );

        return new Tuple2<>(functionName, functionBody);
    }

    private String generateCodeFromOperatorForInvariant(OperatorType op, ReturnType returnType, String lhs, String rhs) {
        var sb = new StringBuilder();
        var lhsValue = "elemL";
        var rhsValue = "elemR";

        var comparisonCode = switch (op) {
            case EQ: yield returnType == ReturnType.STRING
                    ? String.format("%s.equals(%s)", lhsValue, rhsValue)
                    : (returnType == ReturnType.NUMBER
                    ? String.format("Double.compare(%s,%s) == 0", lhsValue, rhsValue)
                    : String.format("%s == %s", lhsValue, rhsValue));
            case NEQ: yield returnType == ReturnType.STRING
                    ? String.format("!%s.equals(%s)", lhsValue, rhsValue)
                    : (returnType == ReturnType.NUMBER
                    ? String.format("Double.compare(%s,%s) == 0", lhsValue, rhsValue)
                    : String.format("%s != %s", lhsValue, rhsValue));
            case GT: yield String.format("%s > %s", lhsValue, rhsValue);
            case GTE: yield String.format("%s >= %s", lhsValue, rhsValue);
            case LT: yield String.format("%s < %s", lhsValue, rhsValue);
            case LTE: yield String.format("%s <= %s", lhsValue, rhsValue);
        };

        var compareLists = String.format(
            """
            for (var elemL : %s) {
              for (var elemR : %s) {
                  if(!(%s)) {
                    return false;
                  }
              }
            }
            return true;
            """, String.format("%s.get()", lhs), String.format("%s.get()", rhs), comparisonCode);
        sb.append(
                String.format(
                        """
                            if (%s.isPresent() && %s.isPresent()) {
                                %s
                            } else {
                                return false;
                            }
                        """, lhs, rhs, compareLists)
        );
        return sb.toString();
    }

    private String generateCodeFromOperandForInvariant(Operand operand, String variableName) {
        return switch (operand.operandType) {
            case ATOM:
                yield generateCodeFromAtom(operand, variableName);
            case QUALIFIED_NAME:
                yield generateCodeFromQualifiedNameForInvariant(operand, variableName);
        };
    }

    private String generateCodeFromQualifiedNameForInvariant(Operand operand, String variableName) {
        var sb = new StringBuilder();
        var eventId = getEventId(operand.value);
        var nodePosition = eventSequence.getEventPositionById(eventId);
        var node = eventSequence.getSequence().get(nodePosition);

        sb.append(
                String.format(
                        """
                        Optional<List<%s>> %s;
                        try {
                        
                        """, toJavaType(operand.returnType), variableName)
        );

        sb.append(
                String.format(
                        """
                            var temp = map.get("%s").stream()
                                 .filter(e -> e.Type.equals("%s"))
                                 .map(e -> e.Content.get("%s")%s)
                                 .collect(Collectors.toList());
                            %s = Optional.ofNullable(temp);
                        } catch (Exception e) {
                            %s = Optional.ofNullable(null);
                        }
                        
                        """, node.getName(), id2Type.get(eventId), getMember(operand.value), toJsonDataType(operand.returnType), variableName, variableName)
        );

        return sb.toString();
    }

    private List<String> translateTermFromWhereClause(SequenceNode currentNode, InvariantsParser.TermContext term) {
        var termText = term.getText();

        termText = termText
                .replace("AND", "&&")
                .replace("OR", "||");

        var resultText = termText;

        final Matcher matcher = pattern.matcher(termText);

        var functions = new ArrayList<String>();

        while (matcher.find()) {
            // (a=b) ==> group0
            var lhs = convertToOperand(matcher.group(1));
            var op = OperatorType.from(matcher.group(2));
            var rhs = convertToOperand(matcher.group(3));

            validateOperation(lhs, op, rhs);

            var tuple = generateCodeFromOperationForWhereClause(lhs, op, rhs, currentNode);
            functions.add(tuple.f1);

            resultText = resultText.replace(matcher.group(0), tuple.f0 + "(event, context)");
        }

        // (A = B OR C = D AND (E > F)) AND (X = Y)
        // (fun123(e, ctx) || fun234(e, ctx) && (fun345(e, ctx))
        patternCodeBuilder.append(
                String.format(
                        """    
                    .where(
                        new IterativeCondition<>() {
                            @Override
                            public boolean filter(Event event, IterativeCondition.Context<Event> context) throws Exception {
                            return %s;
                    }
                })
                """, resultText));

        return functions;
    }

    private Tuple2<String, String> generateCodeFromOperationForWhereClause(Operand lhs,
                                                           OperatorType operatorType,
                                                           Operand rhs,
                                                           SequenceNode currentNode) {
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

        var lhsCode = generateCodeFromOperandForWhereClause(lhs, "lhs", currentNode);
        var rhsCode = generateCodeFromOperandForWhereClause(rhs, "rhs", currentNode);
        var comparisonCode = generateCodeFromOperator(operatorType, lhs.returnType, "lhs","rhs");

        var functionName = "__" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var functionBody = String.format(
                """
                static boolean %s(Event event, IterativeCondition.Context<Event> context) {
                        %s
                        %s
                        %s
                };
                """, functionName, lhsCode, rhsCode, comparisonCode
        );

        return new Tuple2<>(functionName, functionBody);
    }

    private String generateCodeFromOperator(OperatorType operatorType, ReturnType returnType, String lhs, String rhs) {
        var sb = new StringBuilder();

        var lhsValue = String.format("%s.get()", lhs);
        var rhsValue = String.format("%s.get()", rhs);

        var comparisonCode = switch (operatorType) {
            case EQ: yield returnType == ReturnType.STRING
                    ? String.format("%s.equals(%s)", lhsValue, rhsValue)
                    : (returnType == ReturnType.NUMBER
                    ? String.format("Double.compare(%s,%s) == 0", lhsValue, rhsValue)
                    : String.format("%s == %s", lhsValue, rhsValue));
            case NEQ: yield returnType == ReturnType.STRING
                    ? String.format("!%s.equals(%s)", lhsValue, rhsValue)
                    : (returnType == ReturnType.NUMBER
                    ? String.format("Double.compare(%s,%s) == 0", lhsValue, rhsValue)
                    : String.format("%s != %s", lhsValue, rhsValue));
            case GT: yield String.format("%s > %s", lhsValue, rhsValue);
            case GTE: yield String.format("%s >= %s", lhsValue, rhsValue);
            case LT: yield String.format("%s < %s", lhsValue, rhsValue);
            case LTE: yield String.format("%s <= %s", lhsValue, rhsValue);
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

    private String generateCodeFromOperandForWhereClause(Operand operand, String variableName, SequenceNode node) {
        return switch (operand.operandType) {
            case ATOM:
                yield generateCodeFromAtom(operand, variableName);
            case QUALIFIED_NAME:
                yield generateCodeFromQualifiedNameForWhereClause(operand, variableName, node);
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

    private String generateCodeFromQualifiedNameForWhereClause(
            Operand operand,
            String variableName,
            SequenceNode currentNode) {

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

        var alreadyExistsInContext = !currentNode.eventIds.contains(eventId);

        if (alreadyExistsInContext) {
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
