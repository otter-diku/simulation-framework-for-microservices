using FluentValidation;

namespace WorkloadGenerator.Data.Models.Operation;

public class TransactionOperationInputBase
{


    public virtual OperationType Type { get; set; }
}

// public class TransactionOperationInputBaseValidator : AbstractValidator<TransactionOperationInputBase>
// {
//     public TransactionOperationInputBaseValidator()
//     {
//         RuleFor(operation => operation.TemplateId)
//             .NotEmpty()
//             .WithMessage($"{nameof(TransactionOperationInputBase)} ID needs to be a non-empty string");
//     }
// }