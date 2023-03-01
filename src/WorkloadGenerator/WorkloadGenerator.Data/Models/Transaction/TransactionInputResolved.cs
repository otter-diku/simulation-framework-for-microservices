using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;

public class TransactionInputResolved : TransactionInputBase
{
}

public class TransactionInputResolvedValidator : AbstractValidator<TransactionInputResolved>
{
    public TransactionInputResolvedValidator()
    {
        Include(new TransactionInputBaseValidator());
    }
}

