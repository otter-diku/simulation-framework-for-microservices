using FluentValidation;

namespace WorkloadGenerator.Data.Models;

public abstract class TransactionOperationInputBase
{
    public string Id { get; set; }
    
    public OperationType Type { get; set; }
    
    public HttpMethod? HttpMethod { get; set; }

    
    // Headers can have duplicate keys so we cannot use a dictionary here
    // TODO: Headers should be parametrized 
    public List<Header>? Headers { get; set; }
    
    // TODO: Query params should be parametrized 
    public List<QueryParameter>? QueryParameters { get; set; }
    
    // We might need to work with URLs with some params that are resolved at runtime so .NET's URI might not be applicable here
    public string? Url { get; set; }

    protected bool Equals(TransactionOperationInputBase other)
    {
        return Id == other.Id && Type == other.Type && HttpMethod == other.HttpMethod && Equals(Headers, other.Headers) && Equals(QueryParameters, other.QueryParameters) && Url == other.Url;
    }

    public override bool Equals(object? obj)
    {
        if (ReferenceEquals(null, obj)) return false;
        if (ReferenceEquals(this, obj)) return true;
        if (obj.GetType() != this.GetType()) return false;
        return Equals((TransactionOperationInputBase)obj);
    }

    public override int GetHashCode()
    {
        return HashCode.Combine(Id, (int)Type, HttpMethod, Headers, QueryParameters, Url);
    }
}

public class QueryParameter
{
    public string Key { get; set; }
    public string Value { get; set; }
}

public class TransactionOperationInputBaseValidator : AbstractValidator<TransactionOperationInputBase>
{
    public TransactionOperationInputBaseValidator()
    {
        RuleFor(operation => operation.Id)
            .NotEmpty()
            .WithMessage($"{nameof(TransactionOperationInputBase)} ID needs to be a non-empty string");

        When(operation => operation.Type == OperationType.Http, () =>
        {
            RuleFor(operation => operation.HttpMethod)
                .NotNull()
                .WithMessage("HTTP operations needs to specify the HTTP method");
            RuleFor(operation => operation.Url)
                .NotEmpty()
                .WithMessage("HTTP operations need to specify URL.");
        });
    }
}


public enum PayloadType
{
    // most likely only JSON payloads will be supported
    Json = 10
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