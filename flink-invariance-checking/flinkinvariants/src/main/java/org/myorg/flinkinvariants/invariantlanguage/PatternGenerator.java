package org.myorg.flinkinvariants.invariantlanguage;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PatternGenerator {

    private EventSequence eventSequence;
    private List<InvariantsParser.TermContext> terms;
    private Map<String, String> id2Type;

    private final StringBuilder patternCodeBuilder = new StringBuilder();

    public PatternGenerator(EventSequence eventSequence,
                            List<InvariantsParser.TermContext> terms,
                            Map<String, String> id2Type) {
        this.eventSequence = eventSequence;
        this.terms = terms;
        this.id2Type = id2Type;
    }

    public Pattern<Event, Event> generatePattern() {
        var firstNode = eventSequence.getSequence().get(0);
        patternCodeBuilder.append("Pattern.<Event>begin("+ firstNode.getName()+ ")");

        addSimpConditionForNode(firstNode, id2Type);


        for (var node : eventSequence.getSequence().stream().skip(1).collect(Collectors.toList())) {
            if (node.Negated) {
                // pattern = updateWithNegatedNode(pattern, node, id2Type, terms);
            } else {
                addSimpConditionForNode(node, id2Type);
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

    private void addSimpConditionForNode(SequenceNode node, Map<String, String> id2Type) {
        var simpleCond = node.EventIds.stream()
                .map(eId -> "|| e.Type.equals(\"" + id2Type.get(eId) + "\")").reduce("", String::concat);
        patternCodeBuilder.append(
                """
                .where(SimpleCondition.of(e ->
                       false ${simpleCond}
                ));""".replace("${simpleCond}", simpleCond)
        );
    }

    private Pattern<Event, Event> updateWithFinalIterativeCondition(Pattern<Event, Event> pattern, EventSequence eventSequence, List<InvariantsParser.TermContext> terms) {

        HashMap<String, Boolean> eventIdToNegated = new HashMap<>();
        for (var node : eventSequence.getSequence()) {
            for (var eventId : node.EventIds) {
                eventIdToNegated.put(eventId, node.Negated);
            }
        }

        var nonNegatedTerms = terms.stream().filter(term -> getReferencedEventIds(term)
                .stream().noneMatch(eventIdToNegated::get))
                .collect(Collectors.toList());

        for (var term : nonNegatedTerms) {
            pattern = pattern.where(createConditionFromTerm(term));
        }

        return pattern;
    }

    private Pattern<Event, Event> updateWithNode(Pattern<Event, Event> pattern, SequenceNode node, Map<String, String> id2Type) {
        return pattern.followedByAny(node.getName())
                .where(SimpleCondition.of(e ->
                        node.EventIds.stream()
                                .map(id2Type::get)
                                .anyMatch(eType -> eType.equals(e.Type))
                ));
    }

    private Pattern<Event, Event> updateWithNegatedNode(Pattern<Event, Event> pattern, SequenceNode node, Map<String, String> id2Type, List<InvariantsParser.TermContext> terms) {
        pattern = pattern.notFollowedBy(node.getName())
                .where(SimpleCondition.of(e ->
                        node.EventIds.stream()
                                .map(id2Type::get)
                                .anyMatch(eType -> eType.equals(e.Type))
                ));

        var relevantTerms = terms.stream()
                .filter(term -> getReferencedEventIds(term)
                        .contains(node.EventIds.get(0)))
                .collect(Collectors.toList());

        for (var term : relevantTerms) {
            pattern = pattern.where(createConditionFromTerm(term));
        }

        return pattern;
    }

    private IterativeCondition<Event> createConditionFromTerm(InvariantsParser.TermContext term) {
        return new IterativeCondition<Event>() {
            @Override
            public boolean filter(Event event, Context<Event> context) throws Exception {
                return transform(term).apply(event, context);
            }
        };
    }

    private BiFunction<Event, IterativeCondition.Context<Event>, Boolean> transform(InvariantsParser.TermContext term) {
        if (term.equality() != null) {
            return transform(term.equality());
        }

        if (term.or() != null) {
            return (event, eventContext) ->
                    transform(term.term(0)).apply(event, eventContext) ||
                    transform(term.term(1)).apply(event, eventContext);
        }

        if (term.and() != null) {
            return (event, eventContext) ->
                    transform(term.term(0)).apply(event, eventContext) &&
                    transform(term.term(1)).apply(event, eventContext);
        }

        return (event, eventContext) -> false;
    }

    private BiFunction<Event, IterativeCondition.Context<Event>, Boolean> transform(InvariantsParser.EqualityContext equality) {
        var lhs = equality.quantity(0);
        var op = equality.EQ_OP().getSymbol().getText();
        var rhs = equality.quantity(1);

        // NOTE: the idea here is that transform() returns a function
        // which takes 2 arguments (event, eventContext) and returns an Object
        // The returned Object can be either:
        //  - a string
        //  - an int
        //  - a boolean
        //  - a single JsonNode
        //  - multiple JsonNodes
        // Therefore we need to make sure to correctly apply the comparison operator
        // between the result of transform(lhs) and transform(rhs)

        var lhsFunction = transform(lhs);
        var rhsFunction = transform(rhs);

        return (event, eventContext) -> {
          var lhsReturnVal = lhsFunction.apply(event, eventContext);
          var rhsReturnVal = rhsFunction.apply(event, eventContext);
          return compare(lhsReturnVal, op, rhsReturnVal);
        };
    }

    private boolean compare(Object lhs, String operator, Object rhs) {

        // TODO: replace all == and equals below with the value of 'operator' arg
        // TODO:
        // if only one side is a list of nodes then it is a valid case and we need to add this
        // example:
        // SEQ (a, b*)
        // WHERE (a.id = b.id)

        // TODO:
        // if both sides are a list of nodes then we should probably throw? Dunno really.
        // example:
        // SEQ (a*, b*)
        // WHERE (a.id = b.id)

        if (lhs instanceof JsonNode && rhs instanceof JsonNode) {
            return ((JsonNode) lhs).asText().equals(((JsonNode) rhs).asText());
        }

        if (lhs instanceof JsonNode) {
            if (rhs instanceof Boolean)
                return ((JsonNode) lhs).asBoolean() == (Boolean) rhs;
            if (rhs instanceof Integer)
                return ((JsonNode) lhs).asInt() == (Integer) rhs;
            if (rhs instanceof String)
                return ((JsonNode) lhs).asText().equals(rhs);
        }

        if (rhs instanceof JsonNode) {
            if (lhs instanceof Boolean)
                return ((JsonNode) rhs).asBoolean() == (Boolean) lhs;
            if (lhs instanceof Integer)
                return ((JsonNode) rhs).asInt() == (Integer) lhs;
            if (lhs instanceof String)
                return ((JsonNode) rhs).asText().equals(lhs);
        }

        if (lhs instanceof Boolean && rhs instanceof Boolean)
            return lhs == rhs;
        if (lhs instanceof Integer && rhs instanceof Integer)
            return lhs == rhs;
        if (lhs instanceof String && rhs instanceof String)
            return lhs.equals(rhs);

        // TODO:
        // otherwise we are trying to compare incompatible types (me thinks), we should throw
        return false;
    }

    private BiFunction<Event, IterativeCondition.Context<Event>, Object> transform(InvariantsParser.QuantityContext quantity) {
        if (quantity.atom() != null) {
            if (quantity.atom().BOOL() != null) {
                return (event, eventContext) -> Boolean.parseBoolean(quantity.getText());
            }
            if (quantity.atom().INT() != null) {
                return (event, eventContext) -> Integer.parseInt(quantity.getText());
            }
            if (quantity.atom().STRING() != null) {
                return (event, eventContext) -> quantity.getText();
            }

            System.out.println("Something went wrong here...");
            return (event, eventContext) -> "";
        }

        var eventId = quantity.qualifiedName().IDENTIFIER(0).getText();
        var relatedNode = eventSequence.getSequence()
                .stream()
                .filter(node -> node.EventIds.contains(eventId))
                .findFirst()
                .orElseThrow();

        // NOTE: it gets weird here. We can check if the event is negated
        // or if it's the last event in the sequence.
        // If so, that means that we need to access 'event', and not the 'context'
        // Otherwise, we can safely assume that we can find this event in the context.

        var position = eventSequence.getEventPositionById(eventId);
        var node = eventSequence
                .getSequence()
                .get(position);

        var isNegatedOrLastEventInSequence = node.Negated ||
                position == eventSequence.getSequence().size() - 1;

        return (event, eventContext) -> {
            if (isNegatedOrLastEventInSequence) {
                // NOTE: (a, b, c+) is a special case:
                //       if we have a condition involving all c events
                //       for example (sum(c) < a.stock) then we can only
                //       check this in the patternMatch function as the iterative
                //       condition will only have access to one c event at a time
                //       additionally the match behaviour is weird meaning for
                //       the sequence: a b c c c
                //       we get (and maybe more matches) in the patternMatch function:
                //       a b c
                //       a b c c
                //       a b c c c
                //       but we only want to check the invariant for
                //       the match a b c c c.
                //
                //       I think if we support a*, and a+ then we also need to support
                //       aggregate function (sum(a.amount), count(a), etc.),
                //       if we do not allow aggregate functions
                //       then * and + are not really useful in any way for writing
                //       invariants I think
                return traverse(event.Content, quantity.qualifiedName());
            } else {
                try {
                    return StreamSupport.stream(eventContext.getEventsForPattern(relatedNode.getName()).spliterator(), false)
                            .map(e -> traverse(e.Content, quantity.qualifiedName()))
                            .collect(Collectors.toList());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private JsonNode traverse(JsonNode node, InvariantsParser.QualifiedNameContext qualifiedName) {
        JsonNode result = node;
        for (var identifier : qualifiedName.IDENTIFIER().stream().skip(1).collect(Collectors.toList())) {
            result = result.get(identifier.getText());
        }
        return result;
    }

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
