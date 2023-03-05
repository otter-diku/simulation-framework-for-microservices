namespace WorkloadGenerator.Data.Models.Generator;

public class GeneratorBase
{
    public string Id { get; set; }

    public virtual GeneratorType Type { get; set; }
    
    public DistributionType Distribution { get; set; }

}