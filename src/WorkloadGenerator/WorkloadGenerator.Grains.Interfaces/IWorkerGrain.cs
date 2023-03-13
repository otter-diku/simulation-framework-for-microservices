using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Grains.Interfaces;

public interface IWorkerGrain : IGrainWithIntegerKey
{
    Task ExecuteTransaction(
        WorkloadInputUnresolved workload,
        TransactionReference txRef,
        TransactionInputUnresolved tx,
        Dictionary<string, ITransactionOperationUnresolved> operations,
        IHttpClientFactory httpClientFactory);
}