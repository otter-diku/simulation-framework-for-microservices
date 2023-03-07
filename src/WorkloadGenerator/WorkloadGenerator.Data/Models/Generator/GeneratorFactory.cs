namespace WorkloadGenerator.Data.Models.Generator;

public class GeneratorFactory
{
    public static IGenerator GetGenerator(GeneratorType generatorType)
    {
        switch (generatorType)
        {
            case GeneratorType.UnsignedInt:
                return new NumberGenerator(true);
            case GeneratorType.SignedInt:
                return new NumberGenerator(false);
            case GeneratorType.String:
                return new StringGenerator();
            case GeneratorType.Guid:
                return new GuidGenerator();
            default:
                throw new ArgumentOutOfRangeException(nameof(generatorType), generatorType, null);
        }
    }
}