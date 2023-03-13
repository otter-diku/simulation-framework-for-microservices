namespace WorkloadGenerator.Data.Models.Operation;

public interface ITransactionOperationUnresolved : ITransactionOperation
{
    void ValidateAndThrow();

    public Argument[]? Arguments { get; set; }

    public DynamicVariable[]? DynamicVariables { get; set; }
}