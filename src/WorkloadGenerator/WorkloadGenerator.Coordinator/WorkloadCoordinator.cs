using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using WorkloadGenerator.Server;
using WorkloadGenerator.Grains;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Coordinator;

/// <summary>
/// This class is responsible for executing a given Workload / scenario
/// by creating the worker grains which execute the requests against
/// the application that is being simulated.
/// </summary>
public class WorkloadCoordinator : IDisposable
{
    private IHost _silo;
    private IClusterClient _client;

    public WorkloadCoordinator()
    { 
        
    }

    public async Task Init()
    {
        _silo = await WorkloadGeneratorServer.StartSiloAsync();
        _client = _silo.Services.GetService<IClusterClient>()!;
    }

    public void StartExecution(int numTransactions)
    {
        var xacts = GenerateTransactionDistribution(numTransactions);
        for (int i = 0; i < numTransactions; i++)
        {
            if (xacts[i] == TransactionType.CatalogAddItem)
            {
                var worker = _client.GetGrain<IWorkerGrain>(i,
                    grainClassNamePrefix: "WorkloadGenerator.Grains.CatalogAddItemGrain");
                worker.ExecuteTransaction().Wait();
            }
            else if (xacts[i] == TransactionType.CatalogUpdatePrice)
            {
                var worker = _client.GetGrain<IWorkerGrain>(i,
                    grainClassNamePrefix: "WorkloadGenerator.Grains.CatalogUpdateItemPriceGrain");
                worker.ExecuteTransaction().Wait();
            }
            else if (xacts[i] == TransactionType.BasketAddItem)
            {
                var worker = _client.GetGrain<IWorkerGrain>(i,
                    grainClassNamePrefix: "WorkloadGenerator.Grains.BasketAddItemGrain");
                worker.ExecuteTransaction().Wait();
            }
        }
    }

    private List<TransactionType> GenerateTransactionDistribution(int numTransactions)
    {
        // want to use config to have certain distribution of xacts 
        var values = Enum.GetValues(typeof(TransactionType));
        var random = new Random();

        var xacts = new List<TransactionType>();
        for (int i = 0; i < numTransactions; i++)
        {
            var randomXact = (TransactionType)values.GetValue(random.Next(values.Length))!;
            xacts.Add(randomXact);
        }

        return xacts;
    }

    public void Dispose()
    {
        _silo.Dispose();
    }
}