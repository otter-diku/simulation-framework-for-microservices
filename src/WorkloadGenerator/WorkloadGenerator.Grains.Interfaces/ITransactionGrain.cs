using Orleans;
using Orleans.Concurrency;

namespace WorkloadGenerator.Grains.Interfaces
{
    public interface ITransactionGrain : IGrainWithGuidKey
    {
        Task Init();

        Task ExecuteTransaction();
    }
}