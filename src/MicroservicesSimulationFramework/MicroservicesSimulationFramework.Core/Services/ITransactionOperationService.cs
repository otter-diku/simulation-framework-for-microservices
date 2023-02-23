using MicroservicesSimulationFramework.Core.Models.Input;
using MicroservicesSimulationFramework.Core.Models.Internal;

namespace MicroservicesSimulationFramework.Core.Services;

public interface ITransactionOperationService
{
    bool TryParseInput(string json, out TransactionOperationInput parsedInput);
    TransactionOperation Convert(TransactionOperationInput transactionOperationInput, Dictionary<string, object>? providedValues = null);
}