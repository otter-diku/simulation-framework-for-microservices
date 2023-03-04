namespace WorkloadGenerator.Data.Models.Generator;

public class GeneratorBase
{
    public string Id { get; set; }

    public GeneratorType Type { get; set; }
    
    public DistributionType Distribution { get; set; }

}

public enum GeneratorType
{
    UnsignedInt = 10,
    SignedInt = 20,
    String = 30,
    Guid = 40
}

public enum DistributionType
{
    Uniform = 10,
    Zipfian = 20
}