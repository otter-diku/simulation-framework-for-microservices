using FluentValidation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public abstract class SleepOperationInputBase : OperationInputBase
{
    public override OperationType Type => OperationType.Sleep;

    public TimeSpanType Units { get; set; }
}

public class SleepOperationInputBaseValidator : AbstractValidator<SleepOperationInputBase>
{
    public SleepOperationInputBaseValidator()
    {
    }
}

public enum TimeSpanType
{
    Milliseconds = 10,
    Seconds = 20,
    Minutes = 30
}