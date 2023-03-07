using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Grains.Interfaces;

public interface IWorkerGrain : IGrainWithGuidKey
{
    public void ExecuteTransaction(
        WorkloadInputUnresolved workload,
        TransactionInputUnresolved tx,
        Dictionary<string, ITransactionOperationUnresolved> operations,
        IHttpClientFactory httpClientFactory);
}