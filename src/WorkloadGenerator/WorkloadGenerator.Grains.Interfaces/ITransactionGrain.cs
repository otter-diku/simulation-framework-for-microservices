using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Grains.Interfaces
{
    public interface ITransactionGrain : IGrainWithIntegerKey
    {
        Task Init(TransactionInputUnresolved unresolved);

        Task ExecuteTransaction(IReadOnlyDictionary<string, object> providedValues,
        IReadOnlyDictionary<string, ITransactionOperationUnresolved> operations);
    }
}