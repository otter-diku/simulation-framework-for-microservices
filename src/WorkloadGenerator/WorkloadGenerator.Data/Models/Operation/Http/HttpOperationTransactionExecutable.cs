namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationTransactionExecutable  : TransactionOperationExecutableBase
{
    public Action<HttpRequestMessage>? PrepareRequestMessage { get; set; }

    public override OperationType Type => OperationType.Http;
}