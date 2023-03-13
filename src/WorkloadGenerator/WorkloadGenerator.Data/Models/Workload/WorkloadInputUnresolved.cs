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
        When(input => input.Generators is not null, () =>
        {
            // check that all generators referenced by transactions exist
            RuleFor(input => input).Must(input => input.Transactions
                    .TrueForAll(txRef =>
                        txRef.Data.TrueForAll(
                            genRef => 
                                input.Generators.Select(g => g.Id).ToHashSet().Contains(genRef.GeneratorReferenceId) 
                    )));
        });        
    }
}