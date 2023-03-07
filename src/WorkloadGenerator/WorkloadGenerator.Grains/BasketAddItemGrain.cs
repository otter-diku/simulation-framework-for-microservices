using System.Net.Http.Json;
using System.Text.Json.Nodes;
using Utilities;
using Orleans.Concurrency;
using WorkloadGenerator.Grains.Interfaces;
using Data.Model;
using DataGenerator.Model;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace WorkloadGenerator.Grains;

[StatelessWorker]
public class BasketAddItemGrain : Grain, ITransactionGrain
{
    public Task Init()
    {
        return Task.CompletedTask;
    }

    public async Task ExecuteTransaction()
    {
        var _client = new HttpClient();
        Console.WriteLine("Starting BasketAddItem Transaction.");

        // randomly draw catalog items from 
        var rnd = new Random();
        var numItemsToBuy = rnd.Next(EshopData.DataGenerator.LargestGeneratedCatalogItemId);
        var res = await _client.GetAsync(Constants.CatalogItemUrl
                                         + $"?pageSize={numItemsToBuy}&pageIndex=0");

        if (!res.IsSuccessStatusCode)
        {
            Console.WriteLine("Getting catalog items failed with: " + res);
        }
        else
        {
            var responseCatalog = await res.Content.ReadFromJsonAsync<JsonObject>();
            // Console.WriteLine(responseCatalog["data"]);
            var catalogItems = JsonConvert
                .DeserializeObject<List<CatalogItem>>(responseCatalog["data"].ToString());

            var basketItems = new List<BasketItem>();
            foreach (var catalogItem in catalogItems)
            {
                if (1 >= catalogItem.AvailableStock) continue;
                var basketItem = new BasketItem();
                // TODO: basket id should be separate id to identify specific basket for a customer
                basketItem.Id = catalogItem.Id.ToString();
                basketItem.ProductId = catalogItem.Id.ToString();
                basketItem.ProductName = catalogItem.Name;
                basketItem.UnitPrice = catalogItem.Price;
                basketItem.OldUnitPrice = catalogItem.Price;
                var quantity = rnd.Next(1, catalogItem.AvailableStock);
                basketItem.Quantity = quantity;
                basketItem.PictureUrl = catalogItem.PictureUri;

                basketItems.Add(basketItem);
            }

            var userId = Constants.AliceUserId;
            var content = new JObject
            {
                // TODO: select random customer for now just use Alice
                { "buyerId", userId },
                { "items", JsonConvert.SerializeObject(basketItems) },
            };
            var basketRequest = new BasketRequest();
            basketRequest.buyerId = userId;
            basketRequest.items = basketItems;

            Console.WriteLine(content.ToString());

            _client.DefaultRequestHeaders.Add("user-id", userId);
            var putResponse = await _client.PostAsJsonAsync(Constants.BasketUrl, basketRequest);

            Console.WriteLine("Resulting response: " + putResponse);
        }
    }
}