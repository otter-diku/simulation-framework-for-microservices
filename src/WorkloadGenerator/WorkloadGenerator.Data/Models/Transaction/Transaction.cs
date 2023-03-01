using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;


public class TransactionInput
{
    public string Id { get; set; }
    public Argument[]? Arguments { get; set; }
    public DynamicVariable?[] DynamicVariables { get; set; }
    public List<Operation> Operations { get; set; }

    public class Operation
    {
        public string OperationReferenceId { get; set; }

        public string Id { get; set; }

        public ProvidedValue?[] ProvidedValues { get; set; }

        public class ProvidedValue
        {
            public string Key { get; set; }
            public object Value { get; set; }
        }
    }
}

public class TransactionInputValidator : AbstractValidator<TransactionInput>
{
    public TransactionInputValidator()
    {
        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !input.Arguments.Select(a => a.Name)
                .Intersect(input.DynamicVariables.Select(dynamicVar => dynamicVar.Name)).Any());
        });
    }
}