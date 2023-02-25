using FluentValidation;

namespace WorkloadGenerator.Data.Models;

public class TransactionOperationInputUnresolved : TransactionOperationInputBase
{
    public Argument[]? Arguments { get; set; }
    
    public Payload? Payload { get; set; }
    
    public DynamicVariable[]? DynamicVariables { get; set; }

}

public class TransactionOperationInputUnresolvedValidator : AbstractValidator<TransactionOperationInputUnresolved>
{
    public TransactionOperationInputUnresolvedValidator()
    {
        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !input.Arguments.Select(a => a.Name)
                .Intersect(input.DynamicVariables.Select(dynamicVar => dynamicVar.Name)).Any());
        });
    }
}

public class Argument
{
    public string Name { get; set; }
    public ArgumentType Type { get; set; }
}

public class DynamicVariable
{
    public string Name { get; set; }
    public DynamicVariableType Type { get; set; }
}

public enum DynamicVariableType
{
    UnsignedInt = 10,
    SignedInt = 20,
    String = 30,
    Guid = 40
}

public class Payload
{
    public PayloadType Type { get; set; }
    
    public object Content { get; set; }
}