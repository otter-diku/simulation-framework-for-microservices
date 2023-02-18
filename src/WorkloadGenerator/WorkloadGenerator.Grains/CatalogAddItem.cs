using System.Text.Json;
using Orleans;
using Orleans.Concurrency;
using WorkloadGenerator.Grains.Interfaces;
using Utilities;


namespace WorkloadGenerator.Grains;

[StatelessWorker]
public class CatalogAddItem : Grain, IWorkerGrain  
{
    private HttpClient? _client;
    
    public Task Init()
    {
        _client = new HttpClient();
        return Task.CompletedTask;
    }

    
    public Task<HttpResponseMessage> ExecuteTransaction()
    {
        //client.PostAsync()
        var item = Data.DataGenerator.GenerateCatalogItems(1, 10, 20)[0];
        
        var content = new StringContent(JsonSerializer.Serialize(item), System.Text.Encoding.UTF8, "application/json");
        _client.DefaultRequestHeaders.Add("x-requestid", "");

        var response = _client.PostAsync(Constants.catalogItemUrl, content);
        Task.WaitAll(response);
        
        Console.WriteLine("Resulting response: " + response.Result);
        return response;
    }
    
}