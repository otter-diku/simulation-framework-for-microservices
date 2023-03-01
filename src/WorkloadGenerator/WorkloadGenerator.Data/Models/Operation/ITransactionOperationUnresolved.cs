namespace WorkloadGenerator.Data.Models.Operation;

public interface ITransactionOperationUnresolved
{
    void ValidateAndThrow();

    public Argument[]? Arguments { get; set; }

    public DynamicVariable[]? DynamicVariables { get; set; }
}