using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public interface ITransactionService
{
    
    bool TryParseInput(string json, out TransactionInput unresolvedInput);
    
}