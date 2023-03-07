namespace WorkloadGenerator.Data.Models.Generator;

public class NumberGenerator : GeneratorBase, IGenerator
{
    
    private Int64 _lastVal;

    private Random _random;

    private bool _unsigned;

    public GeneratorType Type => GeneratorType.UnsignedInt;

    public NumberGenerator(bool unsigned)
    {
        _unsigned = unsigned;
        _random = new Random();
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