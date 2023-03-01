namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationResponseInput
{
    public HttpOperationResponsePayloadInput Payload { get; set; }
    public HttpOperationResponseHeadersInput Headers { get; set; }
}

public class HttpOperationResponseHeadersInput
{
    public ReturnValue[] ReturnValues { get; set; }
}

public class HttpOperationResponsePayloadInput
{
    public HttpPayloadType Type { get; set; }
    public ReturnValue[] ReturnValues { get; set; }
}