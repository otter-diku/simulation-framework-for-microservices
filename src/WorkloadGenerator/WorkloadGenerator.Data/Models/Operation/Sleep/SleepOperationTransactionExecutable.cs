namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public class SleepOperationTransactionExecutable : TransactionOperationExecutableBase
{
    public Func<Task> Sleep { get; set; }
}