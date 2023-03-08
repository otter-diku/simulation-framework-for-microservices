namespace Utilities
{
    public class Constants
    {
        public const int SiloPort = 11111;
        public const int GatewayPort = 30000;
        public const string ClusterId = "LocalTestCluster";
        public const string ServiceId = "WorkloadGenerator";

        public const string logFile =
            "/home/pt/repos/simulation-framework-for-microservices/src/WorkloadGenerator/log.txt";

        public const string AliceUserId = "10000000-0000-0000-0000-000000000000";
        public const string BobUserId = "20000000-0000-0000-0000-000000000000";

        // Url
        public const string CatalogItemUrl = "http://localhost:5101/catalog-api/api/v1/Catalog/items";
        public const string BasketUrl = "http://localhost:5103/basket-api/api/v1/Basket";
    }
}