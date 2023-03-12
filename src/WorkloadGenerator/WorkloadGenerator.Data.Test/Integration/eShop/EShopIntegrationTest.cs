namespace WorkloadGenerator.Data.Test.Integration.eShop;

public class EShopIntegrationTest
{
    [Test]
    public void ParseTransactionTest()
    {
        var json = File.ReadAllText("Integration/eShop/Transaction/tx_add_items_to_basket.json");

    }
}