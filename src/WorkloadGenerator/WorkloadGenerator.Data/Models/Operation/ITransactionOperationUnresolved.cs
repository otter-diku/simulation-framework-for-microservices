using System.Text.Json;
using System.Text.Json.Serialization;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Operation.Sleep;

namespace WorkloadGenerator.Data.Models.Operation;

public interface ITransactionOperationUnresolved : ITransactionOperation
{
    void ValidateAndThrow();

    public Argument[]? Arguments { get; set; }

    public DynamicVariable[]? DynamicVariables { get; set; }
}

// ReSharper disable once InconsistentNaming
public class ITransactionOperationUnresolvedJsonConverter : JsonConverter<ITransactionOperationUnresolved>
{
    public override ITransactionOperationUnresolved? Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType != JsonTokenType.StartObject)
            throw new JsonException();

        using var jsonDocument = JsonDocument.ParseValue(ref reader);

        if (!jsonDocument.RootElement.TryGetProperty(nameof(TransactionOperationInputBase.Type).ToLower(),
                out var typeProperty))
        {
            throw new JsonException();
        }

        var jsonString = jsonDocument.RootElement.GetRawText();

        if (string.Equals(typeProperty.GetString(), OperationType.Http.ToString(),
                StringComparison.CurrentCultureIgnoreCase))
        {
            return JsonSerializer.Deserialize<HttpOperationInputUnresolved>(jsonString, options);
        }

        if (string.Equals(typeProperty.GetString(), OperationType.Sleep.ToString(),
                StringComparison.CurrentCultureIgnoreCase))
        {
            return JsonSerializer.Deserialize<SleepOperationInputUnresolved>(jsonString, options);
        }

        throw new ArgumentOutOfRangeException();
    }

    public override void Write(Utf8JsonWriter writer, ITransactionOperationUnresolved value, JsonSerializerOptions options)
    {
        switch (value)
        {
            case HttpOperationInputUnresolved httpOperation:
                JsonSerializer.Serialize(writer, httpOperation, options);
                break;
            case SleepOperationInputUnresolved sleepOperation:
                JsonSerializer.Serialize(writer, sleepOperation, options);
                break;
            default:
                throw new ArgumentOutOfRangeException();
        }
    }
}