using WorkloadGenerator.Data.Models;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionOperationService
{
    bool TryParseInput(string json, out TransactionOperationInputUnresolved parsedInput);

    TransactionOperationInputResolved<T> Resolve<T>(TransactionOperationInputUnresolved transactionOperationInputUnresolved,
        Dictionary<string, object>? providedValues = null);
    TransactionOperationInputResolved Resolve(TransactionOperationInputUnresolved transactionOperationInputUnresolved,
        Dictionary<string, object>? providedValues = null);
    
    TransactionOperation Convert<T>(TransactionOperationInputResolved<T> resolvedInput);
    
    TransactionOperation Convert(TransactionOperationInputResolved resolvedInput);
}