using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace MicroservicesSimulationFramework.Core.Models;

public record WorkloadGeneratorInputUnvalidated(
    List<(string FileName, string Content)>? Operations,
    List<(string FileName, string Content)>? Transactions,
    List<(string FileName, string Content)>? Workloads);

public record WorkloadGeneratorInputValidated(
    Dictionary<string, ITransactionOperationUnresolved> Operations,
    Dictionary<string, TransactionInputUnresolved> Transactions,
    Dictionary<string, WorkloadInputUnresolved> Workloads);