using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Client;

public interface IWorkloadCoordinator
{
    Task ScheduleWorkload(
        WorkloadInputUnresolved workloadToRun,
        Dictionary<string, TransactionInputUnresolved> transactions,
        Dictionary<string, IOperationUnresolved> operations);
}