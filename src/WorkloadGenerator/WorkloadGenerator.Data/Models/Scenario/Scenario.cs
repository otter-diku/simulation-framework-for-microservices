using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Models.Scenario;

public class Scenario
{
    public Variable?[] variables;

    public Transaction[] transactions;
}

public class Variable
{
    public string VariableId { get; set; }
    
    public VariableType VariableType { get; set; }

    public int count;
}

public enum VariableType
{
}

public class Transaction
{
    public string TransactionReferenceId { get; set; }

    public string Id { get; set; }

    public int amount;
    
}


public enum DistributionType
{
    uniform,
    zipfian,
    normal
}