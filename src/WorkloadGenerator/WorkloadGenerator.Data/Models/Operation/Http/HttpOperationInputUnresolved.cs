using FluentValidation;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationInputUnresolved : HttpOperationInputBase, IOperationUnresolved
{
    public Argument[]? Arguments { get; set; }
    public DynamicVariable[]? DynamicVariables { get; set; }
    public HttpOperationRequestPayloadUnresolved? RequestPayload { get; set; }

    public void ValidateAndThrow()
    {
        var validator = new HttpOperationInputUnresolvedValidator();
        validator.ValidateAndThrow(this);
    }

    public string TemplateId { get; set; }
}

public class HttpOperationInputUnresolvedValidator : AbstractValidator<HttpOperationInputUnresolved>
{
    public HttpOperationInputUnresolvedValidator()
    {
        Include(new HttpOperationInputBaseValidator());

        RuleFor(operation => operation.TemplateId)
            .NotEmpty()
            .WithMessage($"{nameof(IOperationUnresolved)} ID needs to be a non-empty string");

        When(input => input.Arguments is not null && input.DynamicVariables is not null, () =>
        {
            RuleFor(input => input).Must(input => !input.Arguments.Select(a => a.Name)
                .Intersect(input.DynamicVariables.Select(dynamicVar => dynamicVar.Name)).Any());
        });
    }
}