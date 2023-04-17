import org.junit.Test;
import org.myorg.flinkinvariants.invariantlanguage.InvariantQueryTranslator;

public class InvariantQueryTranslationTest {

    @Test
    public void GenerateInvariant() {
        var translator = new InvariantQueryTranslator();
        var query = """
                customer-decision-event
                  topic: customer-decision-event-queue
                  schema: {date, insuranceQuoteRequestId, quoteAccepted}
                                
                policy-created-event
                  topic: policy-created-event-queue
                  schema: {date, insuranceQuoteRequestId, policyId}
                                
                EVENT SEQ (customer-decision-event cd, policy-created-event pc)
                WHERE cd.insuranceQuoteRequestId = pc.insuranceQuoteRequestId AND cd.quoteAccepted = true
                WITHIN 10 milli""";

        translator.translateQuery(query, "TestInvariant",
                "src/main/java/org/myorg/flinkinvariants/invariantcheckers/TestInvariant.java");
    }
}
