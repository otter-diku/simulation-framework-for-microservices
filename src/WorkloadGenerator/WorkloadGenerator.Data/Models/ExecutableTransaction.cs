using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models;

public class ExecutableTransaction
{
    public TransactionInputUnresolved Transaction { get; set; }
    
    public Dictionary<string, ITransactionOperationUnresolved> Operations { get; set; }
    
    public Dictionary<string, object> ProvidedValues { get; set; }

}