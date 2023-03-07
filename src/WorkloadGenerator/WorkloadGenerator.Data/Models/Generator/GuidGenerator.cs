namespace WorkloadGenerator.Data.Models.Generator;

public class GuidGenerator : GeneratorBase, IGenerator
{
    public GeneratorType Type => GeneratorType.Guid;

    public object Next()
    {
        throw new NotImplementedException();
    }
}
