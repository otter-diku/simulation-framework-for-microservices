namespace WorkloadGenerator.Data.Models.Generator;

public class StringGenerator : GeneratorBase, IGenerator
{

    public override GeneratorType Type => GeneratorType.String;

    public object Next()
    {
        return "1000-00000-00000000";
    }
}