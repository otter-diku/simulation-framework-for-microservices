namespace WorkloadGenerator.Data.Models.Generator;

public class GeneratorBase
{
    public string Id { get; set; }

    public virtual GeneratorType Type { get; set; }

    public DistributionType Distribution { get; set; }
    
    public object? Constant { get; set; }
    
    public int? Min { get; set; }
    
    public int? Max { get; set; }

}