using Orleans;
using Orleans.Concurrency;

namespace WorkloadGenerator.Grains.Interfaces
{
    public interface IWorkerGrain : IGrainWithIntegerKey
    {

        Task Init();
        
        Task ExecuteTransaction();
        
    }
}
