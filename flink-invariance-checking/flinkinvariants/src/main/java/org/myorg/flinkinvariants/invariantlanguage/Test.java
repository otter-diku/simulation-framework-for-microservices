package org.myorg.flinkinvariants.invariantlanguage;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.myorg.invariants.parser.InvariantsBaseListener;
import org.myorg.invariants.parser.InvariantsLexer;
import org.myorg.invariants.parser.InvariantsParser;

import java.util.ArrayList;
import java.util.List;

public class Test {

    public static class InvariantLanguage2CEPListener extends InvariantsBaseListener {
        public Boolean firstEvent = true;
        public int equalityNum = 0;
        public List<String> equalities = new ArrayList<>();

        public StringBuilder invariantBuilder = new StringBuilder();

        String templateFirstEvent = """
                public static Pattern<Event, ?> invariant = Pattern.<Event>begin("IDENTIFIER")
                .where(new SimpleCondition<>() {
                    @Override
                    public boolean filter(Event event) throws Exception {
                        return event.Type.equals("IDENTIFIER");
                    }
                })""";

        String templateSubsequentEvent = """
            .notFollowedBy("IDENTIFIER")
            .where(new SimpleCondition<Event>() {
                @Override
                public boolean filter(Event event) throws Exception {
                    return event.Type.equals("IDENTIFIER");
                }
            })""";

        @Override
        public void enterEventId(InvariantsParser.EventIdContext ctx) {

            if (firstEvent) {
                invariantBuilder.append(templateFirstEvent.replace("IDENTIFIER", ctx.IDENTIFIER().toString()));
                firstEvent = false;
                return;
            }
            invariantBuilder.append(templateSubsequentEvent.replace("IDENTIFIER", ctx.IDENTIFIER().toString()));
        }

        @Override
        public void enterWhere_clause(InvariantsParser.Where_clauseContext ctx) {
            String startIterativeCondition = """
            .where(new IterativeCondition<>() {
                @Override
                public boolean filter(Event event, Context<Event> context) throws Exception {
                """;
            invariantBuilder.append(startIterativeCondition);
        }

        @Override
        public void exitWhere_clause(InvariantsParser.Where_clauseContext ctx) {

            var operators = ctx.OP();
            invariantBuilder.append("return ");

            var it = equalities.iterator();
            for (int i = 0; i < equalities.size() - 1; i++) {
                invariantBuilder.append(it.next());

                var op = operators.get(i);
                var javaOp = switch (op.toString()) {
                    case "AND" -> " && ";
                    case "OR" -> " || ";
                    default -> "Unexpected Operator";
                };
                invariantBuilder.append(javaOp);
            }
            invariantBuilder.append(it.next()).append(";");


            String endIterativeCondition = """
                  }
              })""";
            invariantBuilder.append(endIterativeCondition);
        }


        @Override
        public void enterEquality(InvariantsParser.EqualityContext ctx) {
            String template = """
                     context.getEventsForPattern("EVENT1").iterator().next().Content.get("ATTR1").equals(
                     context.getEventsForPattern("EVENT2").iterator().next().Content.get("ATTR2"))""";

            var event1 = ctx.children.get(0).getChild(0);
            var attribute1 = ctx.children.get(0).getChild(2);
            var operator = ctx.children.get(1);
            var event2 = ctx.children.get(2).getChild(0);
            var attribute2 = ctx.children.get(2).getChild(2);
            var result = template.replace("EVENT1", event1.toString())
                    .replace("EVENT2", event2.toString())
                    .replace("ATTR1", attribute1.toString())
                    .replace("ATTR2", attribute2.toString())
                    .replace("EQ", "eq" + equalityNum);
            equalityNum++;
            equalities.add(result);
        }



        @Override
        public void enterTime(InvariantsParser.TimeContext ctx) {
            int duration = Integer.parseInt(ctx.INT().toString());
            String unit = ctx.TIME().toString();

            var within = switch (unit) {
                case "sec" -> ".within(Time.seconds("+ duration + "));";
                case "min" -> ".within(Time.min(" + duration + "));";
                case "hour" -> ".within(Time.hour(" + duration + "));";
                default -> "Unexpected time unit";
            };
            invariantBuilder.append(within);
        }

        @Override
        public void exitInvariant(InvariantsParser.InvariantContext ctx) {
            var invariantCode = invariantBuilder.toString();
            System.out.println(invariantCode);
        }
    }

    public static void main(String[] args) throws Exception {
        // create a CharStream that reads from standard input
        var invariant = "EVENT SEQ (E_1 e_1, E_2 e_2, E_3 e_3) WITHIN 5 sec";
        var invariant2 = """
                EVENT SEQ (E_1 e_1, E_2 e_2, E_3 e_3)
                WHERE e_1.id = e_2.id AND e_2.id = e_3.id AND e_1.id = e_3.id
                WITHIN 5 sec""";

        ANTLRInputStream input = new ANTLRInputStream(invariant2);
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
     }
}