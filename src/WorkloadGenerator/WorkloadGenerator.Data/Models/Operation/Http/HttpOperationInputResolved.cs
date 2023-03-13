using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationInputResolved : HttpOperationInputBase, ITransactionOperationResolved
{
    public HttpOperationRequestPayloadResolvedBase? RequestPayload { get; set; }
    public string TemplateId { get; set; }
}

public class HttpOperationRequestPayloadResolvedBaseConverter : JsonConverter<HttpOperationRequestPayloadResolvedBase>
{
    /*
     *  Used this approach: https://umamaheswaran.com/2022/07/29/how-to-do-polymorphic-serialization-deserialization-in-c-system-text-json/
     *  More on polymorphic deserialization:
     *   https://learn.microsoft.com/en-us/dotnet/standard/serialization/system-text-json/converters-how-to?pivots=dotnet-7-0#support-polymorphic-deserialization
     */

    public override HttpOperationRequestPayloadResolvedBase? Read(ref Utf8JsonReader reader, Type typeToConvert,
        JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.StartObject)
            throw new JsonException();

        using var jsonDocument = JsonDocument.ParseValue(ref reader);

        if (!jsonDocument.RootElement.TryGetProperty(nameof(HttpOperationRequestPayloadResolvedBase.Type).ToLower(),
                out var typeProperty))
        {
            throw new JsonException();
        }

        if (typeProperty.GetString().ToLower() != HttpPayloadType.Json.ToString().ToLower())
        {
            throw new JsonException();
        }

        var jsonString = jsonDocument.RootElement.GetRawText();
        return JsonSerializer.Deserialize<JsonPayloadResolved>(jsonString, options);
    }

    public override void Write(Utf8JsonWriter writer, HttpOperationRequestPayloadResolvedBase value,
        JsonSerializerOptions options)
    {
        JsonSerializer.Serialize(writer, (object)value, options);
    }
}