namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public class SleepOperationExecutable : OperationExecutableBase
{
    public Func<Task> Sleep { get; set; }

    public override OperationType Type => OperationType.Sleep;
}