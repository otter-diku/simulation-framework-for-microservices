using System.Text.Json;
using Orleans;
using Orleans.Concurrency;
using WorkloadGenerator.Grains.Interfaces;
using Utilities;


namespace WorkloadGenerator.Grains;

[StatelessWorker]
public class CatalogAddItemGrain : Grain, IWorkerGrain  
{
    private HttpClient? _client;
    
    public Task Init()
    {
        _client = new HttpClient();
        return Task.CompletedTask;
    }

    
    public async Task ExecuteTransaction()
    {
        _client = new HttpClient();
        Console.WriteLine("Starting CatalogAddItem Transaction.");
        var item = Data.DataGenerator.GenerateCatalogItem(10, 20);
        
        Console.WriteLine("Generated item: " + JsonSerializer.Serialize(item));
        var content = new StringContent(JsonSerializer.Serialize(item), System.Text.Encoding.UTF8, "application/json");
        _client.DefaultRequestHeaders.Add("x-requestid", "");

        var response = await _client.PostAsync(Constants.catalogItemUrl, content);
        
        Console.WriteLine("Resulting response: " + response);
    }
    
}