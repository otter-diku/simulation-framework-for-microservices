using FluentValidation;

namespace WorkloadGenerator.Data.Models;

public class Transaction
{
    public string Id { get; set; }
    public Argument[]? Arguments { get; set; }
    public DynamicVariable[]? DynamicVariables { get; set; }
    public List<TransactionOperation> Operations { get; set; }
}

public class TransactionValidator : AbstractValidator<Transaction>
{
    public TransactionValidator()
    {
        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !input.Arguments.Select(a => a.Name)
                .Intersect(input.DynamicVariables.Select(dynamicVar => dynamicVar.Name)).Any());
        });
    }
}