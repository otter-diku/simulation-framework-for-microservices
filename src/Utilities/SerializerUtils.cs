using System.Text.Json;
using System.Text.Json.Serialization;

namespace Utilities;

public static class SerializerUtils
{
    public static JsonSerializerOptions GetGlobalJsonSerializerOptions(Action<JsonSerializerOptions> after)
    {
        var options = GetJsonSerializerOptions();
        after(options);
        return options;
    }

    public static JsonSerializerOptions GetGlobalJsonSerializerOptions()
    {
        return GetJsonSerializerOptions();
    }

    private static JsonSerializerOptions GetJsonSerializerOptions() => new(
        new JsonSerializerOptions()
        {
            WriteIndented = true,
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            PropertyNameCaseInsensitive = true,
            Converters =
            {
                new JsonStringEnumConverter(JsonNamingPolicy.CamelCase)
            }
        });
}