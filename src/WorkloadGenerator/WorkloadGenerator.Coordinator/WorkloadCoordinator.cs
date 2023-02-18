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

    public WorkloadCoordinator()
    {
    }
    
    public async Task Init()
    {

        // start orleans server
        // TODO need to start orleans client to spawn grain
    }

    public void StartExecution()
    {
        var grainFactory = _silo.Services.GetRequiredService<IGrainFactory>();
        
        // would start all grains in a loop 
        var grain = grainFactory.GetGrain<IWorkerGrain>(new Guid());
        

    }

    public List<TransactionType> generateTranscationDistribution(int numClients)
    {
        // want to use config to have certain distribution of xacts 
        var values = Enum.GetValues(typeof(TransactionType));
        var random = new Random();

        var xacts = new List<TransactionType>();
        for (int i = 0; i < numClients; i++)
        {
            var randomXact = (TransactionType) values.GetValue(random.Next(values.Length))!;
            xacts.Add(randomXact);
        }

        return xacts;
    }

    public void Dispose()
    {
        _silo.Dispose();
    }
}