using WorkloadGenerator.Data.Models;

namespace WorkloadGenerator.Client;

public interface IWorkloadScheduler
{
    void Init(int maxConcurrentTransactions);
    Task SubmitTransaction(ExecutableTransaction executableTransaction);
}