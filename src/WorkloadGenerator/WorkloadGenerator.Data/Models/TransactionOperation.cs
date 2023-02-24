using FluentValidation;

namespace WorkloadGenerator.Data.Models;

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