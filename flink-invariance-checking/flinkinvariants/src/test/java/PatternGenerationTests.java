import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.IterativeCondition;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.junit.Test;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.flinkinvariants.invariantlanguage.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class PatternGenerationTests {


    /*
    * INPUT:
        A a
          topic: a-topic
          schema: {id:string, price:number, hasFlag:bool}
        B b
          topic: b-topic
          schema: {id:string}
        C c
          topic: c-topic
          schema: {id:string, hasFlag:bool}

        SEQ (a, !b, c)
        WITHIN 1 sec
        WHERE (a.id = b.id) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
        ON FULL MATCH false
    * */


    @Test
    public void thisWasActuallyGeneratedByOurCuttingEdgeAlgorithm() {
        Pattern.<Event>begin("[a]").where(SimpleCondition.of(e -> e.Type.equals("A")
                ))
                .notFollowedBy("[b]")
                .where(SimpleCondition.of(e -> e.Type.equals("B")
                ))
                .where(
                        new IterativeCondition<>() {
                            @Override
                            public boolean filter(Event event, Context<Event> context) throws Exception {
                                Optional<String> lhs;
                                try {
                                    var temp = context.getEventsForPattern("[a]")
                                            .iterator()
                                            .next();
                                    lhs = Optional.of(temp.Content.get("id").asText());
                                } catch (Exception e) {
                                    lhs = Optional.ofNullable(null);
                                }


                                Optional<String> rhs;
                                try {
                                    var temp = event;
                                    rhs = Optional.of(temp.Content.get("id").asText());
                                } catch (Exception e) {
                                    rhs = Optional.ofNullable(null);
                                }


                                if (lhs.isPresent() && rhs.isPresent()) {
                                    return lhs.get().equals(rhs.get());
                                } else {
                                    return false;
                                }

                            }
                        })
                .followedByAny("[c]")
                .where(SimpleCondition.of(e -> e.Type.equals("C")
                ))
                .where(
                        new IterativeCondition<>() {
                            @Override
                            public boolean filter(Event event, Context<Event> context) throws Exception {
                                Optional<Double> lhs;
                                try {
                                    var temp = context.getEventsForPattern("[a]")
                                            .iterator()
                                            .next();
                                    lhs = Optional.of(temp.Content.get("price").asDouble());
                                } catch (Exception e) {
                                    lhs = Optional.ofNullable(null);
                                }


                                var rhs = Optional.of(42.0);
                                if (lhs.isPresent() && rhs.isPresent()) {
                                    return lhs.get() > rhs.get();
                                } else {
                                    return false;
                                }

                            }
                        })
                .where(
                        new IterativeCondition<>() {
                            @Override
                            public boolean filter(Event event, Context<Event> context) throws Exception {
                                Optional<Boolean> lhs;
                                try {
                                    var temp = context.getEventsForPattern("[a]")
                                            .iterator()
                                            .next();
                                    lhs = Optional.of(temp.Content.get("hasFlag").asBoolean());
                                } catch (Exception e) {
                                    lhs = Optional.ofNullable(null);
                                }


                                Optional<Boolean> rhs;
                                try {
                                    var temp = event;
                                    rhs = Optional.of(temp.Content.get("hasFlag").asBoolean());
                                } catch (Exception e) {
                                    rhs = Optional.ofNullable(null);
                                }


                                if (lhs.isPresent() && rhs.isPresent()) {
                                    return lhs.get() != rhs.get();
                                } else {
                                    return false;
                                }

                            }
                        });
    }

    @Test
    public void TestPatternGeneration_0() {
        var query =
                """
                        A a
                          topic: a-topic
                          schema: {id:string, price:number, hasFlag:bool}
                        B b
                          topic: b-topic
                          schema: {id:string}
                        C c
                          topic: c-topic
                          schema: {id:string, hasFlag:bool}
                                       
                        SEQ (a, !b, c)
                        WITHIN 1 sec
                        WHERE (a.id = b.id) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
                        ON FULL MATCH false""";
        var translator = new InvariantTranslator();
        var translationResult = translator.translateQuery(query, null, null);

        var patternGenerator = new PatternGenerator(
                translationResult.sequence,
                translationResult.whereClauseTerms,
                translationResult.id2Type,
                translationResult.schemata,
                translationResult.within,
                translationResult.onFullMatch,
                translationResult.onPartialMatch);

        var pattern = patternGenerator.generatePattern();
    }

//    @Test
//    public void TestPatternGeneration() {
//        var query =
//                """
//                        A a
//                          topic: a-topic
//                          schema: {id:string, price:number, hasFlag:bool}
//                        B b
//                          topic: b-topic
//                          schema: {id:string}
//                        C c
//                          topic: c-topic
//                          schema: {id:string, hasFlag:bool}
//
//                        SEQ (a, !b, c)
//                        WITHIN 1 sec
//                        WHERE (a.id = b.id) AND (a.price > 42) AND (a.hasFlag != c.hasFlag)
//                        ON FULL MATCH false""";
//        var translator = new InvariantTranslator();
//        var terms = translator.getTermsFromQuery(query);
//
//        Map<String, String> id2Type = new HashMap<>();
//        id2Type.put("a", "A");
//        id2Type.put("b", "B");
//        id2Type.put("c", "C");
//
//
//        EventSequence seq = new EventSequence();
//        seq.addNode(new SequenceNode(
//                        false,
//                        SequenceNodeQuantifier.ONCE,
//                        Stream.of("a").collect(Collectors.toList()),
//                        0
//                )
//        );
//        seq.addNode(new SequenceNode(
//                        true,
//                        SequenceNodeQuantifier.ONCE,
//                        Stream.of("b").collect(Collectors.toList()),
//                        1
//                )
//        );
//        seq.addNode(new SequenceNode(
//                        false,
//                        SequenceNodeQuantifier.ONCE,
//                        Stream.of("c").collect(Collectors.toList()),
//                        2
//                )
//        );
//
//        var schemata = new HashMap<String, Map<String, String>>();
//
//        schemata.put("a", Map.of("id", "string", "price", "number", "hasFlag", "bool"));
//        schemata.put("b", Map.of("id", "string"));
//        schemata.put("c", Map.of("id", "string", "hasFlag", "bool"));
//
//        var patternGenerator = new PatternGenerator(seq, terms, id2Type, schemata, translationResult.within, translationResult.onFullMatch, translationResult.onPartialMatch);
//        var pattern = patternGenerator.generatePattern();
//    }
}
