using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionOperationService
{
    bool TryParseInput(string json, out ITransactionOperationUnresolved unresolvedInput);

    bool TryResolve(ITransactionOperationUnresolved unresolvedInput,
        Dictionary<string, object>? providedValues, out ITransactionOperationResolved resolvedInput);
    
    bool TryConvertToExecutable(ITransactionOperationResolved resolvedInput, out TransactionOperationExecutableBase transactionOperationbaseExecutable);
}