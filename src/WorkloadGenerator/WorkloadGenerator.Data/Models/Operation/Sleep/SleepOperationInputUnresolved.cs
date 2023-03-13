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

    public string TemplateId { get; set; }
}

public class SleepOperationInputUnresolvedValidator : AbstractValidator<SleepOperationInputUnresolved>
{
    public SleepOperationInputUnresolvedValidator()
    {
        Include(new SleepOperationInputBaseValidator());
        
        RuleFor(operation => operation.TemplateId)
            .NotEmpty()
            .WithMessage($"{nameof(ITransactionOperationUnresolved)} ID needs to be a non-empty string");        
    }
}