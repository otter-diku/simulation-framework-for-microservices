namespace WorkloadGenerator.Data.Models.Generator;

public class NumberGenerator : GeneratorBase, IGenerator
{
    
    private Int64 _lastVal;

    private Random _random;

    private bool _unsigned;
    private int? _min;
    private int? _max;

    public GeneratorType Type => GeneratorType.UnsignedInt;

    public NumberGenerator(bool unsigned, int? optionalMin = null, int? optionalMax = null)
    {
        _unsigned = unsigned;
        _random = new Random();
        _min = optionalMin;
        _max = optionalMax;
    }
    
    public Int64 LastValue()
    {
        return this._lastVal;
    }

    public object Next()
    {
        int next;
        if (_min is not null && _max is not null)
        {
            next = _random.Next(_min.Value, _max.Value);
        }
        else
        {
            next = _random.Next();            
        }
        _lastVal = next;
        return next;
    }
}