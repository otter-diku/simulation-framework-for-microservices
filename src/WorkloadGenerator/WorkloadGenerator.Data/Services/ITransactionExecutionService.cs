using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionExecutionService
{
    Task Run(
        TransactionInputUnresolved transaction,
        Dictionary<string, object> providedValues,
        Dictionary<string, IOperationUnresolved> operationsDictionary);
}