using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;

public class OperationReference
{
    public string OperationReferenceId { get; set; }
    public string Id { get; set; } = null!;
}

public class OperationReferenceValidator : AbstractValidator<OperationReference>
{
    public OperationReferenceValidator()
    {
        RuleFor(opRef => opRef.Id)
            .NotEmpty();
        RuleFor(opRef => opRef.OperationReferenceId)
            .NotEmpty();
    }
}