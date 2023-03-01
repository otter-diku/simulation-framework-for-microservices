using FluentValidation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public abstract class SleepOperationInputBase : TransactionOperationInputBase
{
    public override OperationType Type => OperationType.Sleep;

    public TimeSpanType Units { get; set; }
}

public class SleepOperationInputBaseValidator : AbstractValidator<SleepOperationInputBase>
{
    public SleepOperationInputBaseValidator()
    {
        Include(new TransactionOperationInputBaseValidator());
    }
}

public enum TimeSpanType
{
    Milliseconds = 10,
    Seconds = 20,
    Minutes = 30
}