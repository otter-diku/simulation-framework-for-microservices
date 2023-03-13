using System.Net.Http.Json;
using Utilities;
using Orleans.Concurrency;
using WorkloadGenerator.Grains.Interfaces;
using Data.Model;

namespace WorkloadGenerator.Grains;

[StatelessWorker]
public class CatalogUpdateItemPriceGrain : Grain, ITransactionGrain
{
    public Task Init()
    {
        return Task.CompletedTask;
    }

    public async Task ExecuteTransaction()
    {
        var _client = new HttpClient();
        Console.WriteLine("Starting CatalogUpdateItemPrice Transaction.");

        // randomly draw item from catalog
        var rnd = new Random();
        var itemId = rnd.Next(EshopData.DataGenerator.LargestGeneratedCatalogItemId);

        CatalogItem catalogItem;
        var res = await _client.GetAsync(Constants.CatalogItemUrl + "/" + itemId);

        if (!res.IsSuccessStatusCode)
        {
            Console.WriteLine("Getting CatalogUpdateItemPrice failed with when getting CatalogItem with: " + res);
        }
        else
        {
            catalogItem = await res.Content.ReadFromJsonAsync<CatalogItem>();
            var priceUpdate = rnd.Next(-1, 10);
            catalogItem.Price += priceUpdate;

            var putResponse = await _client.PutAsJsonAsync(Constants.CatalogItemUrl, catalogItem);

            Console.WriteLine("Resulting response: " + putResponse);
        }
    }
}