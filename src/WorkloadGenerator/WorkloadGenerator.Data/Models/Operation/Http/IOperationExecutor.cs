namespace WorkloadGenerator.Data.Models.Operation.Http;

public interface IOperationExecutor
{
    bool CanHandle(ITransactionOperationResolved resolved);
    Task<Dictionary<string, object>> Execute(ITransactionOperationResolved resolved);
}