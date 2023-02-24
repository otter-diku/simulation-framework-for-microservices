using FluentValidation;

namespace WorkloadGenerator.Data.Models.Internal;

public class TransactionOperation
{
    public Action<HttpRequestMessage> PrepareRequestMessage { get; set; }
}

public class TransactionOperationValidator : AbstractValidator<TransactionOperation>
{
    public TransactionOperationValidator()
    {
        
    }
}