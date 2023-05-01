import org.junit.Test;
import org.myorg.flinkinvariants.invariantlanguage.InvariantTranslator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class InvariantPositiveParseTests {

    @Test
    public void TestEventSeq1() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, b)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq2() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq2_0() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                               
                SEQ (a, !b, c*)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";


        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq3() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, b+, c)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq4() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, b*, c)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq5() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, (b | c))
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEventSeq6() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a*, (b | c | d)+)
                WITHIN 1 sec
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestProductPriceInvariant() {
        var translator = new InvariantTranslator();
        var query =
                """
                ProductPriceChangedIntegrationEvent pc1
                  topic: eshop_event_bus
                  schema: {ProductId, NewPrice}
                ProductPriceChangedIntegrationEvent pc2
                  topic: eshop_event_bus
                  schema: {ProductId, NewPrice}
                ProductBoughtIntegrationEvent pb
                  topic: eshop_event_bus
                  schema: {ProductId, Units, Price}
                                
                SEQ (pc1, !pc2, pb)
                WITHIN 2 min
                WHERE (pc1.ProductId = pb.ProductId) AND (pc1.ProductId = pc2.ProductId)
                ON FULL MATCH (pc1.NewPrice = pb.Price)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestEshopBasketDeletedAfterUserDeleted() {
        var translator = new InvariantTranslator();
        var query =
                """
                UserDeletedEvent ud
                  topic: eshop_event_bus
                  schema: {userId}
                BasketDeletedEvent bd
                  topic: eshop_event_bus
                  schema: {userId}
                                
                SEQ (ud, bd)
                WITHIN 2 min
                WHERE (ud.userId = bd.userId)
                ON PARTIAL MATCH false
                """;

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }


    @Test
    public void TestLakeSideBusinessLogic() {
        var translator = new InvariantTranslator();
        var query =
                """
                insurance-quote-request iqr
                  topic: insurance-quote-request-topic
                  schema: {customer, insurance-type}
                  
                SEQ (iqr)
                WHERE (iqr.insurance-type = 'car-insurance')
                ON FULL MATCH (iqr.customer.address.country = 'Switzerland')
                """;

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }
    @Test
    public void TestWhereClause() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a*, (b | c | d)+)
                WITHIN 1 sec
                WHERE (a.id = b.id OR a.id = c.id) AND (b.price < c.price)
                ON FULL MATCH (a.id = 42)""";

        var translationResult = translator.translateQuery(
                query,
                "TestInvariant",
                "");
        assertEquals(0, translationResult.getNumberOfSyntaxErrors());
        assertFalse(translationResult.isSemanticAnalysisFailed());
    }

    @Test
    public void TestWhereClause2() {
        var translator = new InvariantTranslator();
        var query =
                """
                A a
                  topic: a-topic
                  schema: {id}
                B b
                  topic: b-topic
                  schema: {id}
                C c
                  topic: c-topic
                  schema: {id}
                               
                SEQ (a, !b, c)
                WITHIN 1 sec
                WHERE (a > 5 OR a < 3) AND (b = 3 AND b.y = a.y) AND (c.x = a.x) 
                ON FULL MATCH (a.id = 42)""";

        var result = translator.translateQuery(
                query,
                "TestInvariant",
                "");

        assertEquals(0, result.getNumberOfSyntaxErrors());
        assertFalse(result.isSemanticAnalysisFailed());
    }
}
