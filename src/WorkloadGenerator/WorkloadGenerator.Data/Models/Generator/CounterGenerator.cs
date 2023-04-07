namespace WorkloadGenerator.Data.Models.Generator;

public class CounterGenerator : GeneratorBase, IGenerator
{
    private int _counter;

    public override GeneratorType Type => GeneratorType.Counter;

    public CounterGenerator(int? start)
    {
        if (start is null)
        {
            _counter = -1;
        }
        else
        {
            _counter = (int)(start - 1);
        }

    }

    public object Next()
    {
        return Interlocked.Increment(ref _counter);
    }
}
