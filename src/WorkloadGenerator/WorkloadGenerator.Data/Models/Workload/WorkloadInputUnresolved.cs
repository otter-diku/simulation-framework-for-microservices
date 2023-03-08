using FluentValidation;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models.Workload;

public class WorkloadInputUnresolved : WorkloadInputBase
{
    
}

public class WorkloadInputUnresolvedValidator : AbstractValidator<WorkloadInputUnresolved>
{
    public WorkloadInputUnresolvedValidator()
    {
        Include(new WorkloadInputBaseValidator());
    }
}