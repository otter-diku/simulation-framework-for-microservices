namespace WorkloadGenerator.Data.Models.Generator;

public class GuidGenerator : GeneratorBase, IGenerator
{
    public override GeneratorType Type => GeneratorType.Guid;

    public object Next()
    {
        throw new NotImplementedException();
    }
}
