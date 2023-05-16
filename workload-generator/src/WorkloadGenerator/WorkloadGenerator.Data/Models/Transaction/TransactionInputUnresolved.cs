using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;

public class TransactionInputUnresolved : TransactionInputBase
{
    public Argument[]? Arguments { get; set; }
    public DynamicVariable[]? DynamicVariables { get; set; }
}


public class TransactionInputUnresolvedValidator : AbstractValidator<TransactionInputUnresolved>
{
    public TransactionInputUnresolvedValidator()
    {
        Include(new TransactionInputBaseValidator());
        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !HasAnyConflictingNames(input));
        });
    }

    private static bool HasAnyConflictingNames(TransactionInputUnresolved inputUnresolved)
    {
        var argumentNames = inputUnresolved.Arguments!.Select(a => a.Name);
        var dynamicVariableNames = inputUnresolved.DynamicVariables!.Select(dynamicVar => dynamicVar.Name);
        return argumentNames.Intersect(dynamicVariableNames).Any();
    }
}