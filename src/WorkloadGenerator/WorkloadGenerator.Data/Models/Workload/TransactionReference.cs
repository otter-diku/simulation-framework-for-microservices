using FluentValidation;

namespace WorkloadGenerator.Data.Models.Scenario;

public class TransactionReference
{
    public string TransactionReferenceId { get; set; } 
    public string Id { get; set; } = null!;

    public int Count { get; set; }
}

public class TransactionReferenceValidator : AbstractValidator<TransactionReference>
{
    public TransactionReferenceValidator()
    {
        RuleFor(txRef => txRef.Id)
            .NotEmpty();
        RuleFor(txRef => txRef.TransactionReferenceId)
            .NotEmpty();
    }
}