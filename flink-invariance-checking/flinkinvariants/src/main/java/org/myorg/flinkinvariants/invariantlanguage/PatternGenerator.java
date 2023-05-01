package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.flink.cep.pattern.Pattern.begin;

public class PatternGenerator {

    private final EventSequence eventSequence;
    private final List<InvariantsParser.TermContext> terms;
    private final Map<String, String> id2Type;

    public PatternGenerator(EventSequence eventSequence,
                            List<InvariantsParser.TermContext> terms,
                            Map<String, String> id2Type) {
        this.eventSequence = eventSequence;
        this.terms = terms;
        this.id2Type = id2Type;
    }

    public Pattern<Event, ?> generatePattern() {
        var firstNode = eventSequence.getSequence().get(0);
        var pattern = Pattern.<Event>begin(firstNode.getName())
                .where(SimpleCondition.of(e ->
                        firstNode.EventIds.stream()
                                .map(id2Type::get)
                                .anyMatch(eType -> eType.equals(e.Type))
                ));
        for (var node : eventSequence.getSequence().stream().skip(1).collect(Collectors.toList())) {
            if (node.Negated) {
                pattern = updateWithNegatedNode(pattern, node, id2Type, terms);
            } else {
                pattern = updateWithNode(pattern, node, id2Type);
            }
        }

        pattern = updateWithFinalIterativeCondition(pattern, eventSequence, terms);

        // TODO: first node not allowed to be negated

        //

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

        var relevantTerms = terms.stream()
                .filter(term -> getReferencedEventIds(term)
                        .contains(node.EventIds.get(0)))
                .collect(Collectors.toList());
        return pattern.notFollowedBy(node.getName())
                .where(SimpleCondition.of(e ->
                        node.EventIds.stream()
                                .map(id2Type::get)
                                .anyMatch(eType -> eType.equals(e.Type))
                ))
                .where(new IterativeCondition<Event>() {
                    @Override
                    public boolean filter(Event event, Context<Event> context) throws Exception {
                        return false;
                    }
                });
    }

    private IterativeCondition<Event> createConditionFromTerm(InvariantsParser.TermContext term) {
        return new IterativeCondition<Event>() {
            @Override
            public boolean filter(Event event, Context<Event> context) throws Exception {

            }
        }
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
        InvariantsParser.QuantityContext lhs = equality.quantity(0);
        var op = equality.EQ_OP().getSymbol();
        var rhs = equality.quantity(1);
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
                .findFirst();

        return (event, eventContext) -> {

        };
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
