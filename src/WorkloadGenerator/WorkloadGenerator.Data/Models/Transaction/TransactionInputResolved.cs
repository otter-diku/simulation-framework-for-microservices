using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;

public class TransactionInputResolved : TransactionInputBase
{
    public Guid Id { get; set; }
    public Dictionary<string, object> ProvidedValues { get; set; }
}

public class TransactionInputResolvedValidator : AbstractValidator<TransactionInputResolved>
{
    public TransactionInputResolvedValidator()
    {
        Include(new TransactionInputBaseValidator());
        RuleFor(t => t.Id).NotEmpty();
    }
}

