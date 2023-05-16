namespace WorkloadGenerator.Data.Models.Operation.Sleep;

public class SleepOperationInputResolved : SleepOperationInputBase, IOperationResolved
{
    public decimal Duration { get; set; }
    public string TemplateId { get; set; }
}