namespace WorkloadGenerator.Data.Models.Generator;

public class StringGenerator : GeneratorBase, IGenerator
{

    public GeneratorType Type => GeneratorType.String;

    public object Next()
    {
        throw new NotImplementedException();
    }
}