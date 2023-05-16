using System.Text.Json.Nodes;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public class JsonPayloadResolved : HttpOperationRequestPayloadResolvedBase
{
    public override HttpPayloadType Type => HttpPayloadType.Json;

    public JsonNode Content { get; set; }
}