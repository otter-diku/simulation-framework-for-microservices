package org.myorg.flinkinvariants.invariantlanguage;

import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.myorg.flinkinvariants.events.Event;
import org.myorg.invariants.parser.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import static org.apache.flink.cep.pattern.Pattern.begin;

public class Test {

    public static class InvariantLanguage2CEPListener extends InvariantsBaseListener {
        public Boolean firstEvent = true;


        // TODO use pattern directly / patter builder?

        String templateFirstEvent = """
            Pattern.<Event>begin("IDENTIFIER")
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
        public void enterEventSchema(InvariantsParser.EventSchemaContext ctx) {

            if (firstEvent) {
                System.out.println(templateFirstEvent.replace("IDENTIFIER", ctx.IDENTIFIER().toString()));
                firstEvent = false;
                return;
            }
            System.out.println(templateSubsequentEvent.replace("IDENTIFIER", ctx.IDENTIFIER().toString()));
            //super.enterEventSchema(ctx);
        }

        @Override
        public void enterTime(InvariantsParser.TimeContext ctx) {
            Integer duration = Integer.parseInt(ctx.INT().toString());
            String unit = ctx.TIME().toString();

            switch (unit) {
                case "sec" -> System.out.println(".within(Time.seconds(%d));".formatted(duration));
                case "min" -> System.out.println(".within(Time.min(%d));".formatted(duration));
                case "hour" -> System.out.println(".within(Time.hour(%d));".formatted(duration));
                default -> System.out.println("Unexpected time unit");
            }
            //super.enterTime(ctx);
        }

        @Override
        public void exitInvariant(InvariantsParser.InvariantContext ctx) {
            super.exitInvariant(ctx);
        }
    }

    public static void main(String[] args) throws Exception {
        // create a CharStream that reads from standard input
        var invariant = "EVENT SEQ (E_1 e_1, E_2 e_2, E_3 e_3) WITHIN 5 sec";
        var invariant2 = """
                EVENT SEQ (E_1 e_1, E_2 e_2, E_3 e_3)
                WHERE e_1.id = e_2.id AND e_2.id = e_3.id
                WITHIN 5 sec""";

        ANTLRInputStream input = new ANTLRInputStream(invariant);
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
        // System.out.println(tree.toStringTree(parser)); // print LISP-style tree
    }
}