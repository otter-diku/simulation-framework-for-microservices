using FluentValidation;
using WorkloadGenerator.Data.Models.Generator;

namespace WorkloadGenerator.Data.Models.Workload;

public abstract class WorkloadInputBase
{
    public string Id { get; set; }

    public List<TransactionReference> Transactions { get; set; }

    public List<GeneratorBase>? Generators { get; set; }

}

public class WorkloadInputBaseValidator : AbstractValidator<WorkloadInputBase>
{
    public WorkloadInputBaseValidator()
    {
        RuleFor(w => w.Id).NotEmpty();
        RuleFor(w => w.Transactions).NotEmpty();
        RuleForEach(w => w.Transactions).SetValidator(new TransactionReferenceValidator());
        When(input => input.Generators is not null, () =>
        {
            // check that all generators referenced by transactions exist
            RuleFor(input => input).Must(input =>
            {
                var generatorIds = input.Generators.Select(g => g.Id).ToHashSet();
                var genRefIds = input.Transactions
                    .SelectMany(t => t.Data)
                    .Select(g => g.GeneratorReferenceId).ToList();
                return genRefIds.TrueForAll(g => generatorIds.Contains(g));
            });
        });
    }
}