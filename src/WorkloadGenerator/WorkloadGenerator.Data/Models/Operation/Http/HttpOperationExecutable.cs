namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationExecutable : OperationExecutableBase
{
    public Action<HttpRequestMessage>? PrepareRequestMessage { get; set; }

    public override OperationType Type => OperationType.Http;
}