using FluentValidation;

namespace WorkloadGenerator.Data.Models.Transaction;

public abstract class TransactionInputBase
{
    public string TemplateId { get; set; }
    public List<OperationReference> Operations { get; set; } = new();
}

public class TransactionInputBaseValidator : AbstractValidator<TransactionInputBase>
{
    public TransactionInputBaseValidator()
    {
        RuleFor(t => t.TemplateId).NotEmpty();
        RuleFor(t => t.Operations).NotEmpty();
        RuleForEach(t => t.Operations).SetValidator(new OperationReferenceValidator());
    }
}