using System.Text.Json.Serialization;
using FluentValidation;

namespace MicroservicesSimulationFramework.Core.Models.Input;

public class TransactionOperationInput
{
    public string Id { get; set; }
    
    public Argument[]? Arguments { get; set; }
    
    public OperationType Type { get; set; }
    
    public HttpMethod? HttpMethod { get; set; }

    public Payload? Payload { get; set; }
    
    // Headers can have duplicate keys so we cannot use a dictionary here
    // TODO: Headers should be parametrized 
    public List<Header>? Headers { get; set; }
    
    // TODO: Query params should be parametrized 
    public List<QueryParameter>? QueryParameters { get; set; }
    
    // We might need to work with URLs with some params that are resolved at runtime so .NET's URI might not be applicable here
    public string? Url { get; set; }
}

public class QueryParameter
{
    public string Key { get; set; }
    public string Value { get; set; }
}

public class TransactionOperationInputValidator : AbstractValidator<TransactionOperationInput>
{
    public TransactionOperationInputValidator()
    {
        RuleFor(operation => operation.Id)
            .NotEmpty()
            .WithMessage($"{nameof(TransactionOperationInput)} ID needs to be a non-empty string");

        When(operation => operation.Type == OperationType.Http, () =>
        {
            RuleFor(operation => operation.HttpMethod)
                .NotNull()
                .WithMessage("HTTP operations needs to specify the HTTP method");

            // TODO: we can only validate once we resolve the URL arguments at runtime
            // RuleFor(operation => operation.Url)
            //     .Must(url => Uri.IsWellFormedUriString(url, UriKind.RelativeOrAbsolute))
            //     .WithMessage("HTTP operations needs to specify a valid URL");
        });
    }
}

public class Payload
{
    public PayloadType Type { get; set; }
    
    public object Content { get; set; }
}

public enum PayloadType
{
    // most likely only JSON payloads will be supported
    Json = 10
}

public class Argument
{
    public string Name { get; set; }
    public ArgumentType Type { get; set; }
    public bool Required { get; set; }
}

public enum ArgumentType
{
    String,
    Number,
    Object,
    Array,
    Boolean,
    Null
}

public class Header
{
    public string Key { get; set; }
    public string Value { get; set; }
}

public enum HttpMethod
{
    Get = 10,
    Post = 20,
    Put = 30,
    Patch = 40,
    Delete = 50 
}

public enum OperationType
{
    Http = 10
}