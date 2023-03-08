namespace WorkloadGenerator.Data.Models.Generator;

public class GeneratorFactory
{
    public static IGenerator GetGenerator(GeneratorBase generatorBase)
    {
        switch (generatorBase.Type)
        {
            case GeneratorType.UnsignedInt:
                return new NumberGenerator(true);
            case GeneratorType.SignedInt:
                return new NumberGenerator(false);
            case GeneratorType.String:
                return new StringGenerator();
            case GeneratorType.Guid:
                return new GuidGenerator();
            case GeneratorType.Constant:
                return new ConstantGenerator(generatorBase.Constant);
            default:
                throw new ArgumentOutOfRangeException(nameof(generatorBase), generatorBase, null);
        }
    }
}