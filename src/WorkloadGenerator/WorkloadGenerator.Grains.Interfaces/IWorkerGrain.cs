using Orleans;
using Orleans.Concurrency;

namespace WorkloadGenerator.Grains.Interfaces
{
    public interface IWorkerGrain : IGrainWithGuidKey
    {

        Task Init();
        
        Task<HttpResponseMessage> ExecuteTransaction();
        
    }
}
