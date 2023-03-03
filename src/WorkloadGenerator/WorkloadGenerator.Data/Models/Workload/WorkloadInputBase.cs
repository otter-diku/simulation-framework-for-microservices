using FluentValidation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models.Scenario;

public abstract class WorkloadInputBase
{
    public string Id { get; set; }
    public List<TransactionReference> Transactions { get; set; } = new();
    
    public List<Generator> Generators { get; set; }
}

public class WorkloadInputBaseValidator : AbstractValidator<WorkloadInputBase>
{
    public WorkloadInputBaseValidator()
    {
        RuleFor(w => w.Id).NotEmpty();
        RuleFor(w => w.Transactions).NotEmpty();
        RuleForEach(w => w.Transactions).SetValidator(new TransactionReferenceValidator());
    }
}