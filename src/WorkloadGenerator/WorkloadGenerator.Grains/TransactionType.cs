namespace WorkloadGenerator.Grains;

public enum TransactionType
{
    CatalogAddItem,
    CatalogDeleteItem,
    CatalogUpdatePrice,
    BasketAddItem,
    BasketDeleteItem,
    BasketCheckout
}