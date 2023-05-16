using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Data.Services;

public interface IWorkloadService
{
    bool TryParseInput(string input, out WorkloadInputUnresolved unresolved);
    bool TryResolve(WorkloadInputUnresolved unresolved,
        HashSet<string> transactionReferenceIds,
        out WorkloadInputResolved resolved);
}