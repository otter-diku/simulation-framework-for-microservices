namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationResponsePayloadInput
{
    public HttpPayloadType Type { get; set; }
    
    public ReturnValue[] ReturnValues { get; set; }
}