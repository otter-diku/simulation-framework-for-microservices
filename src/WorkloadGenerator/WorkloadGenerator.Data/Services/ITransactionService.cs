using System.Collections.Immutable;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionService
{
    bool TryParseInput(string input, out TransactionInputUnresolved unresolved);
    bool TryResolve(TransactionInputUnresolved unresolved, 
         IReadOnlyDictionary<string, object> providedValues, 
        out TransactionInputResolved resolved);

    bool Validate(TransactionInputUnresolved unresolved, IReadOnlySet<string> operationReferenceIds);
}
