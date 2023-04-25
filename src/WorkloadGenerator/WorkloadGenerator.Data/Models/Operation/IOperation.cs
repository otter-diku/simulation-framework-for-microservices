namespace WorkloadGenerator.Data.Models.Operation;

public interface IOperation
{
    public string TemplateId { get; set; }
    public OperationType Type { get; set; }
}