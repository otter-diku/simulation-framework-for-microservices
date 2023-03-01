namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public class SleepOperationInputResolved : SleepOperationInputBase, ITransactionOperationResolved
{
    public decimal Duration { get; set; }
}