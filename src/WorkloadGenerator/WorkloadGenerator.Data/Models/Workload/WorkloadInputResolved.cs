using FluentValidation;

namespace WorkloadGenerator.Data.Models.Workload;

public class WorkloadInputResolved : WorkloadInputBase
{
    public Dictionary<string, TransactionReference> TransactionReferences { get; set; }
}

public class WorkloadInputResolvedValidator : AbstractValidator<WorkloadInputResolved>
{
    public WorkloadInputResolvedValidator()
    {
        Include(new WorkloadInputBaseValidator());
    }
}