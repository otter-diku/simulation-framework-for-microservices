namespace WorkloadGenerator.Data.Models;

public class TransactionOperationInputUnresolved : TransactionOperationInputBase
{
    public Argument[]? Arguments { get; set; }
    
    public Payload? Payload { get; set; }

}

public class Argument
{
    public string Name { get; set; }
    public ArgumentType Type { get; set; }
    public bool Required { get; set; }
}

public class Payload
{
    public PayloadType Type { get; set; }
    
    public object Content { get; set; }
}