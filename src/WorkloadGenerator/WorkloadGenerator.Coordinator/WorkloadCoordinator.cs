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
    }
    
    
    public void Dispose()
    {
        _silo.Dispose();
    }
}