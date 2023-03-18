using FluentValidation;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public abstract class HttpOperationInputBase : TransactionOperationInputBase
{
    public override OperationType Type => OperationType.Http;

    public string Url { get; set; }

    public HttpMethod HttpMethod { get; set; }

    public List<Header>? Headers { get; set; }

    // TODO: Query params should be parametrized 
    public List<QueryParameter>? QueryParameters { get; set; }
    
    public HttpOperationResponseInput? Response { get; set; }
}

public class HttpOperationInputBaseValidator : AbstractValidator<HttpOperationInputBase>
{
    public HttpOperationInputBaseValidator()
    {
        Include(new TransactionOperationInputBaseValidator());

        RuleFor(httpOp => httpOp.HttpMethod)
            .IsInEnum();

        RuleFor(httpOp => httpOp.Url)
            .NotEmpty();
    }
}