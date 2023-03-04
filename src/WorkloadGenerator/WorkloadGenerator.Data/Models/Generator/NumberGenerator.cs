namespace WorkloadGenerator.Data.Models.Generator;

public class NumberGenerator : GeneratorBase<int>
{
    
    private Int64 lastVal;

    private Random random;

    public NumberGenerator()
    {
        this.random = new Random();
    }

    public Int64 lastValue()
    {
        return this.lastVal;
    }

    public override int Next()
    {
        var next = random.Next();
        lastVal = next;
        return next;
    }
}