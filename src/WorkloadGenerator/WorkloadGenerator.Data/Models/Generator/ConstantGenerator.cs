namespace WorkloadGenerator.Data.Models.Generator;

public class ConstantGenerator : GeneratorBase, IGenerator
{
    public GeneratorType Type => GeneratorType.Constant;

    private readonly object _constant;

    public ConstantGenerator(object constant)
    {
        _constant = constant;
    }
    public object Next()
    {
        return _constant;
    }
}