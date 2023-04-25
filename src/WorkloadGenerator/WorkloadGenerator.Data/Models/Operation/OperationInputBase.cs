namespace WorkloadGenerator.Data.Models.Operation;

public class OperationInputBase : IOperation
{
    public string TemplateId { get; set; }
    public virtual OperationType Type { get; set; }
}