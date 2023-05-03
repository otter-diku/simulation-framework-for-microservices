package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.api.java.tuple.Tuple2;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternGenerator {

    final String equalityRegex = "(['0-9a-zA-Z\\._]+)\\s*(=|!=|>|<|>=|<=)\\s*(['0-9a-zA-Z\\._]+)";
    private EventSequence eventSequence;
    private List<InvariantsParser.TermContext> terms;
    private Map<String, String> id2Type;
    private HashMap<String, List<Tuple2<String, String>>> schemata;

    private final StringBuilder patternCodeBuilder = new StringBuilder();

    public PatternGenerator(EventSequence eventSequence,
                            List<InvariantsParser.TermContext> terms,
                            Map<String, String> id2Type, HashMap<String, List<Tuple2<String, String>>> schemata) {
        this.eventSequence = eventSequence;
        this.terms = terms;
        this.id2Type = id2Type;
        this.schemata = schemata;
    }

    public String generatePattern() {
        var firstNode = eventSequence.getSequence().get(0);
        patternCodeBuilder.append("Pattern.<Event>begin("+ firstNode.getName()+ ")");

        addSimpleConditionForNode(firstNode, id2Type);


        for (var node : eventSequence.getSequence().stream().skip(1).collect(Collectors.toList())) {
            if (node.Negated) {
                addNotFollowedBy(node);

                terms.stream()
                        .filter(term -> getReferencedEventIds(term).contains(node.EventIds.get(0)))
                        .forEach(this::addWhereClauseFromTerm);
            } else {
                addFollowedByAny(node);
                addSimpleConditionForNode(node, id2Type);
            }
        }

//        for (var node : eventSequence.getSequence().stream().skip(1).collect(Collectors.toList())) {
//            if (node.Negated) {
//                pattern = updateWithNegatedNode(pattern, node, id2Type, terms);
//            } else {
//                pattern = updateWithNode(pattern, node, id2Type);
//            }
//        }

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

        return null;
    }

    private void addWhereClauseFromTerm(InvariantsParser.TermContext term, SequenceNode node, List<SequenceNode> sequenceNodes) throws Exception {

        String string = "(A.x = B.y && 5 > C.z && (f.x = y.z && (x.z <= f.f)) || 'x' = D.f || 'f' != D.h)\n";

        string = string.replace("AND", "&&")
                .replace("OR", "||");

        final Pattern pattern = Pattern.compile(equalityRegex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(string);

        while (matcher.find()) {
            var lhs = matcher.group(1);
            var op = matcher.group(2);
            var rhs = matcher.group(3);

            boolean isMostRecentNodeLhs;
            boolean isMostRecentNodeRhs;
            String typeLhs = null;
            String typeRhs = null;
            if (isQualifiedName(lhs)) {
                var eventId = getEventId(lhs);
                var memberTypes = schemata.get(eventId);
                isMostRecentNodeLhs = node.EventIds.contains(eventId);
                // TODO: assumes flat event schema
                var member = getMember(lhs);
                var tuple = memberTypes.stream().filter(t -> t.f0.equals(member))
                        .findFirst();
                typeLhs = tuple.get().f1;
            } else {

            }
            if (isQualifiedName(rhs)) {
                var eventId = getEventId(rhs);
                var memberTypes = schemata.get(eventId);
                isMostRecentNodeRhs = node.EventIds.contains(eventId);
                // TODO: assumes flat event schema
                var member = getMember(rhs);
                var tuple = memberTypes.stream().filter(t -> t.f0.equals(member))
                        .findFirst();
                typeRhs = tuple.get().f1;
            }

            if (!typeRhs.equals(typeLhs)) {
                throw new Exception("Lhs type does not equal Rhs type.");
            }

            // bool, num, string, : = !=
            // num : <= < > >=
            if ((typeRhs.equals("bool") || typeRhs.equals("string"))
                    && List.of("<=", "<", ">", ">=").contains(op))
            {
                throw new Exception("Using numeric operator on incompatible type.");
            }

            // if event is not the most recent node
            // then use context.getEvents() (maybe multiple events)
            // a b b c   (a, b, c) WHERE a.id = b.id

            // a b1 b2 c with b1.id != a and b2.id == a
            // (a,b,c) WHERE a.id = b.id

            // (a, b, !c, d) WHERE c.id = b.id AND a.id = b.id
            // stream: a b1 b2 e d
            // matches:
            //  - a b1 e
            //  - a b2 e

            // (a, b+, !c, d) WHERE c.id = b.id AND a.id = b.id
            // stream: a b1 b2 e d
            // matches:
            //  - a b1 e
            //  - a b1 b2 e -> then have b1 and b2 for comparing
            // itertiveCond(
            //  List<Event> bs = context.getEvents(b)
            //  List<Event> as = context.getEvents(a)
            //


            // (a, b+, !c, d) filter only by type
            // stream: a b1 b2 c1 c2 d
            //  matches: a b1 b2 d

            // a b1 b2 c1 b3 c2 d (c1, c2 wont break pattern)
            // matches (followedByAny on types + regex):
            // !c --> followedByAny().SimpCond( e -> e.type = c)
            // - a b1 b2 c1 d
            // - a b1 b2 c2 d
            // -

            // filter(e -> type )
            // c d a b a d b c c
            

            // a b1 b2 c   (a,b,c)
            // a b1 c
            // a b2 c


            //



            // A.x -> context.getEvents('A')
            // =
            // B.y

            // (a, !b, c)
            // WHERE (b.id = a.id) AND (a.id = c.id)

            // "[a,b]" -> or case
            // begin("a").notfollowedBy("b")
            // .where(IterativeCond(
            //

            // (a, (b | c), !d, e) WHERE (d.id = b.id OR d.x = c.x)
            // var bOrCs = context.getEvents()


            // (a, (b1 | b2), !d, e) WHERE (d.id = b.id OR d.x = c.x)

        }

        // (A.a.c = B.b.c || C.d.a = D.x.y && (X.z.x > Z.c.a))

        // the most important regex ever
        // /['0-9a-zA-Z\._]+\s(=|!=|>|<|>=|<=)\s['0-9a-zA-Z\._]+/gm

        // a.x = 'f=c'
        // 1. blindly replace ORs and ANDs
        // 2. blindly replace symbols (=, >=..) if necessary >=, =
        // '=' -> '==' '!=' | '<' | '<=' | '>' | '>=';



    }

    private String getMember(String qualifiedName) {
        return qualifiedName.split("\\.")[1];
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

    private void addNotFollowedBy(SequenceNode node) {
        patternCodeBuilder.append(String.format(".notFollowedBy(\"%s\")\n", node.getName()));
    }

    private void addFollowedByAny(SequenceNode node) {
        patternCodeBuilder.append(String.format(".followedByAny(\"%s\")\n", node.getName()));
    }

    private void addSimpleConditionForNode(SequenceNode node, Map<String, String> id2Type) {
        var simpleCondition = node.EventIds.stream()
                        .map(eId -> String.format("e.Type.equals(\"%s\")\n", id2Type.get(eId)))
                        .collect(Collectors.joining("||"));

        patternCodeBuilder.append(String.format(".where(SimpleCondition.of(e -> %s))\n", simpleCondition));
    }

//    private Pattern<Event, Event> updateWithFinalIterativeCondition(Pattern<Event, Event> pattern, EventSequence eventSequence, List<InvariantsParser.TermContext> terms) {
//
//        HashMap<String, Boolean> eventIdToNegated = new HashMap<>();
//        for (var node : eventSequence.getSequence()) {
//            for (var eventId : node.EventIds) {
//                eventIdToNegated.put(eventId, node.Negated);
//            }
//        }
//
//        var nonNegatedTerms = terms.stream().filter(term -> getReferencedEventIds(term)
//                .stream().noneMatch(eventIdToNegated::get))
//                .collect(Collectors.toList());
//
//        for (var term : nonNegatedTerms) {
//            pattern = pattern.where(createConditionFromTerm(term));
//        }
//
//        return pattern;
//    }
//
//    private Pattern<Event, Event> updateWithNode(Pattern<Event, Event> pattern, SequenceNode node, Map<String, String> id2Type) {
//        return pattern.followedByAny(node.getName())
//                .where(SimpleCondition.of(e ->
//                        node.EventIds.stream()
//                                .map(id2Type::get)
//                                .anyMatch(eType -> eType.equals(e.Type))
//                ));
//    }
//
//    private Pattern<Event, Event> updateWithNegatedNode(Pattern<Event, Event> pattern, SequenceNode node, Map<String, String> id2Type, List<InvariantsParser.TermContext> terms) {
//        pattern = pattern.notFollowedBy(node.getName())
//                .where(SimpleCondition.of(e ->
//                        node.EventIds.stream()
//                                .map(id2Type::get)
//                                .anyMatch(eType -> eType.equals(e.Type))
//                ));
//
//        var relevantTerms = terms.stream()
//                .filter(term -> getReferencedEventIds(term)
//                        .contains(node.EventIds.get(0)))
//                .collect(Collectors.toList());
//
//        for (var term : relevantTerms) {
//            pattern = pattern.where(createConditionFromTerm(term));
//        }
//
//        return pattern;
//    }
//
//    private IterativeCondition<Event> createConditionFromTerm(InvariantsParser.TermContext term) {
//        return new IterativeCondition<Event>() {
//            @Override
//            public boolean filter(Event event, Context<Event> context) throws Exception {
//                return transform(term).apply(event, context);
//            }
//        };
//    }
//
//    private BiFunction<Event, IterativeCondition.Context<Event>, Boolean> transform(InvariantsParser.TermContext term) {
//        if (term.equality() != null) {
//            return transform(term.equality());
//        }
//
//        if (term.or() != null) {
//            return (event, eventContext) ->
//                    transform(term.term(0)).apply(event, eventContext) ||
//                    transform(term.term(1)).apply(event, eventContext);
//        }
//
//        if (term.and() != null) {
//            return (event, eventContext) ->
//                    transform(term.term(0)).apply(event, eventContext) &&
//                    transform(term.term(1)).apply(event, eventContext);
//        }
//
//        return (event, eventContext) -> false;
//    }
//
//    private BiFunction<Event, IterativeCondition.Context<Event>, Boolean> transform(InvariantsParser.EqualityContext equality) {
//        var lhs = equality.quantity(0);
//        var op = equality.EQ_OP().getSymbol().getText();
//        var rhs = equality.quantity(1);
//
//        // NOTE: the idea here is that transform() returns a function
//        // which takes 2 arguments (event, eventContext) and returns an Object
//        // The returned Object can be either:
//        //  - a string
//        //  - an int
//        //  - a boolean
//        //  - a single JsonNode
//        //  - multiple JsonNodes
//        // Therefore we need to make sure to correctly apply the comparison operator
//        // between the result of transform(lhs) and transform(rhs)
//
//        var lhsFunction = transform(lhs);
//        var rhsFunction = transform(rhs);
//
//        return (event, eventContext) -> {
//          var lhsReturnVal = lhsFunction.apply(event, eventContext);
//          var rhsReturnVal = rhsFunction.apply(event, eventContext);
//          return compare(lhsReturnVal, op, rhsReturnVal);
//        };
//    }
//
//    private boolean compare(Object lhs, String operator, Object rhs) {
//
//        // TODO: replace all == and equals below with the value of 'operator' arg
//        // TODO:
//        // if only one side is a list of nodes then it is a valid case and we need to add this
//        // example:
//        // SEQ (a, b*)
//        // WHERE (a.id = b.id)
//
//        // TODO:
//        // if both sides are a list of nodes then we should probably throw? Dunno really.
//        // example:
//        // SEQ (a*, b*)
//        // WHERE (a.id = b.id)
//
//        if (lhs instanceof JsonNode && rhs instanceof JsonNode) {
//            return ((JsonNode) lhs).asText().equals(((JsonNode) rhs).asText());
//        }
//
//        if (lhs instanceof JsonNode) {
//            if (rhs instanceof Boolean)
//                return ((JsonNode) lhs).asBoolean() == (Boolean) rhs;
//            if (rhs instanceof Integer)
//                return ((JsonNode) lhs).asInt() == (Integer) rhs;
//            if (rhs instanceof String)
//                return ((JsonNode) lhs).asText().equals(rhs);
//        }
//
//        if (rhs instanceof JsonNode) {
//            if (lhs instanceof Boolean)
//                return ((JsonNode) rhs).asBoolean() == (Boolean) lhs;
//            if (lhs instanceof Integer)
//                return ((JsonNode) rhs).asInt() == (Integer) lhs;
//            if (lhs instanceof String)
//                return ((JsonNode) rhs).asText().equals(lhs);
//        }
//
//        if (lhs instanceof Boolean && rhs instanceof Boolean)
//            return lhs == rhs;
//        if (lhs instanceof Integer && rhs instanceof Integer)
//            return lhs == rhs;
//        if (lhs instanceof String && rhs instanceof String)
//            return lhs.equals(rhs);
//
//        // TODO:
//        // otherwise we are trying to compare incompatible types (me thinks), we should throw
//        return false;
//    }
//
//    private BiFunction<Event, IterativeCondition.Context<Event>, Object> transform(InvariantsParser.QuantityContext quantity) {
//        if (quantity.atom() != null) {
//            if (quantity.atom().BOOL() != null) {
//                return (event, eventContext) -> Boolean.parseBoolean(quantity.getText());
//            }
//            if (quantity.atom().INT() != null) {
//                return (event, eventContext) -> Integer.parseInt(quantity.getText());
//            }
//            if (quantity.atom().STRING() != null) {
//                return (event, eventContext) -> quantity.getText();
//            }
//
//            System.out.println("Something went wrong here...");
//            return (event, eventContext) -> "";
//        }
//
//        var eventId = quantity.qualifiedName().IDENTIFIER(0).getText();
//        var relatedNode = eventSequence.getSequence()
//                .stream()
//                .filter(node -> node.EventIds.contains(eventId))
//                .findFirst()
//                .orElseThrow();
//
//        // NOTE: it gets weird here. We can check if the event is negated
//        // or if it's the last event in the sequence.
//        // If so, that means that we need to access 'event', and not the 'context'
//        // Otherwise, we can safely assume that we can find this event in the context.
//
//        var position = eventSequence.getEventPositionById(eventId);
//        var node = eventSequence
//                .getSequence()
//                .get(position);
//
//        var isNegatedOrLastEventInSequence = node.Negated ||
//                position == eventSequence.getSequence().size() - 1;
//
//        return (event, eventContext) -> {
//            if (isNegatedOrLastEventInSequence) {
//                // NOTE: (a, b, c+) is a special case:
//                //       if we have a condition involving all c events
//                //       for example (sum(c) < a.stock) then we can only
//                //       check this in the patternMatch function as the iterative
//                //       condition will only have access to one c event at a time
//                //       additionally the match behaviour is weird meaning for
//                //       the sequence: a b c c c
//                //       we get (and maybe more matches) in the patternMatch function:
//                //       a b c
//                //       a b c c
//                //       a b c c c
//                //       but we only want to check the invariant for
//                //       the match a b c c c.
//                //
//                //       I think if we support a*, and a+ then we also need to support
//                //       aggregate function (sum(a.amount), count(a), etc.),
//                //       if we do not allow aggregate functions
//                //       then * and + are not really useful in any way for writing
//                //       invariants I think
//                return traverse(event.Content, quantity.qualifiedName());
//            } else {
//                try {
//                    return StreamSupport.stream(eventContext.getEventsForPattern(relatedNode.getName()).spliterator(), false)
//                            .map(e -> traverse(e.Content, quantity.qualifiedName()))
//                            .collect(Collectors.toList());
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        };
//    }
//
//    private JsonNode traverse(JsonNode node, InvariantsParser.QualifiedNameContext qualifiedName) {
//        JsonNode result = node;
//        for (var identifier : qualifiedName.IDENTIFIER().stream().skip(1).collect(Collectors.toList())) {
//            result = result.get(identifier.getText());
//        }
//        return result;
//    }

    /*

    equality
    : quantity EQ_OP quantity;

    quantity
        : qualifiedName
        | atom
        ;

    atom
        : BOOL
        | INT
        | STRING
        ;

    qualifiedName
        : IDENTIFIER ('.' IDENTIFIER)*
        ;



    term
    : equality
    | term and term
    | term or term
    | lpar term rpar
    ;
    *
    * */


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
