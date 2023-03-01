using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionService
{
    bool TryParseInput(string input, out TransactionInputUnresolved unresolved);
    bool TryResolve(TransactionInputUnresolved unresolved, 
        Dictionary<string, object> providedValues, 
        HashSet<string> operationReferenceIds, 
        out TransactionInputResolved resolved);
}
