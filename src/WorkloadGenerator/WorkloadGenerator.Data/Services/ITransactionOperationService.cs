using WorkloadGenerator.Data.Models.Input;
using WorkloadGenerator.Data.Models.Internal;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionOperationService
{
    bool TryParseInput(string json, out TransactionOperationInput parsedInput);
    TransactionOperation Convert(TransactionOperationInput transactionOperationInput, Dictionary<string, object>? providedValues = null);
}