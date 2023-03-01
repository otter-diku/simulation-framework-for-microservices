namespace WorkloadGenerator.Data.Models.Operation;

public abstract class TransactionOperationExecutableBase
{
    public virtual OperationType Type { get; set; }
}