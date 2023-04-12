using FluentValidation;

namespace WorkloadGenerator.Data.Models.Workload;

public class TransactionReference
{
    public string Id { get; set; }

    public string TransactionReferenceId { get; set; }

    public int Count { get; set; }
    
    public int? Percentage { get; set; }

    public List<GeneratorReference> Data { get; set; } = new();


}

public class TransactionReferenceValidator : AbstractValidator<TransactionReference>
{
    public TransactionReferenceValidator()
    {
        RuleFor(txRef => txRef.Id).NotEmpty();
        RuleFor(txRef => txRef.TransactionReferenceId)
            .NotEmpty();
        RuleFor(txRef => txRef.Count)
            .NotEmpty();
    }
}