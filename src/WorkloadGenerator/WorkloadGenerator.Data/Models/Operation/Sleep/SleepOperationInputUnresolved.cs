using FluentValidation;

namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public class SleepOperationInputUnresolved : SleepOperationInputBase, ITransactionOperationUnresolved
{
    public Argument[]? Arguments { get; set; }
    public DynamicVariable[]? DynamicVariables { get; set; }

    public string Duration { get; set; }

    public void ValidateAndThrow()
    {
        var validator = new SleepOperationInputUnresolvedValidator();
        validator.ValidateAndThrow(this);
    }
}

public class SleepOperationInputUnresolvedValidator : AbstractValidator<SleepOperationInputUnresolved>
{
    public SleepOperationInputUnresolvedValidator()
    {
        Include(new SleepOperationInputBaseValidator());
    }
}