namespace WorkloadGenerator.Data.Models.Generator;

public class NumberGenerator : GeneratorBase, IGenerator
{

    private Int64 _lastVal;

    private Random _random;

    public GeneratorType Type => GeneratorType.UnsignedInt;

    public NumberGenerator()
    {
        this._random = new Random();
    }

    public Int64 LastValue()
    {
        return this._lastVal;
    }

    public object Next()
    {
        var next = _random.Next();
        _lastVal = next;
        return next;
    }
}