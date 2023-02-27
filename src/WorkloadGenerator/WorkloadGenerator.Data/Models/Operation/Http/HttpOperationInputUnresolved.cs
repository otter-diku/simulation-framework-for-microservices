using FluentValidation;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationInputUnresolved : HttpOperationInputBase, ITransactionOperationUnresolved
{
    public Argument[]? Arguments { get; set; }
    public DynamicVariable[]? DynamicVariables { get; set; }
    public ReturnValue[]? ReturnValues { get; set; }
    public HttpOperationRequestPayloadUnresolved? RequestPayload { get; set; }

    public void ValidateAndThrow()
    {
        var validator = new HttpOperationInputUnresolvedValidator();
        validator.ValidateAndThrow(this);
    }
}

public class HttpOperationInputUnresolvedValidator : AbstractValidator<HttpOperationInputUnresolved>
{
    public HttpOperationInputUnresolvedValidator()
    {
        Include(new HttpOperationInputBaseValidator());
        
        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !input.Arguments.Select(a => a.Name)
                .Intersect(input.DynamicVariables.Select(dynamicVar => dynamicVar.Name)).Any());
        });
    }
}