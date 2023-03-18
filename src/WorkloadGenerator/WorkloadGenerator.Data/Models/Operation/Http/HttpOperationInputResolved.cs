namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationInputResolved : HttpOperationInputBase, ITransactionOperationResolved
{
    public HttpOperationRequestPayloadResolvedBase? RequestPayload { get; set; }
}