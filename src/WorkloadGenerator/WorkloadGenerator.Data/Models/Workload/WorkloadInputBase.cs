using FluentValidation;
using WorkloadGenerator.Data.Models.Generator;

namespace WorkloadGenerator.Data.Models.Workload;

public abstract class WorkloadInputBase
{
    public string Id { get; set; }
    
    public List<TransactionReference> Transactions { get; set; }
    
    public List<GeneratorBase<object>>? Generators { get; set; }
    
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