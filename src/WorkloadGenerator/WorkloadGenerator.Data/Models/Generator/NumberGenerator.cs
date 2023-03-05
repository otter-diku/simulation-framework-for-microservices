namespace WorkloadGenerator.Data.Models.Generator;

public class NumberGenerator : GeneratorBase, IGenerator
{
    
    private Int64 lastVal;

    private Random random;

    public GeneratorType Type => GeneratorType.UnsignedInt;

    public NumberGenerator()
    {
        this.random = new Random();
    }

    public Int64 lastValue()
    {
        return this.lastVal;
    }

    public object Next()
    {
        var next = random.Next();
        lastVal = next;
        return next;
    }
}