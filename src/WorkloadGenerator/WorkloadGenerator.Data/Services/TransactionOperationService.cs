using System.Collections;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Web;
using FluentValidation;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Input;
using WorkloadGenerator.Data.Models.Internal;
using HttpMethod = System.Net.Http.HttpMethod;

namespace WorkloadGenerator.Data.Services;

public class TransactionOperationService : ITransactionOperationService
{
    private readonly ILogger<TransactionOperationService> _logger;

    // for payloads we need to replace the quotes since we might be replacing this string with a number/array/null/boolean
    // e.g. "key1": "{{arg1}}" => "key1": false 
    // the reason we need the quotes in the first place, is because `"key1": @arg1` is not a valid JSON
    private readonly Regex _payloadArgumentRegex = new("\"{{([^}]*)}}\"");
    
    // for url paths we do not need expect any quotes so we just need to replace the `{{arg-name}}`
    private readonly Regex _urlPathArgumentRegex = new("{{([^}]*)}}");
    
    // for url paths we do not need expect any quotes so we just need to replace the `@@arg-name@@`
    private readonly Regex _urlPathReferenceRegex = new("@@([^}]*)@@");
    
    public TransactionOperationService(ILogger<TransactionOperationService> logger)
    {
        _logger = logger;
    }
    
    private readonly JsonSerializerOptions _jsonSerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters =
        {
            new JsonStringEnumConverter(JsonNamingPolicy.CamelCase)
        }
    };

    private readonly TransactionOperationInputValidator _validator = new();

    public bool TryParseInput(string json, out TransactionOperationInput parsedInput)
    {
        try
        {
            parsedInput = JsonSerializer.Deserialize<TransactionOperationInput>(json, _jsonSerializerOptions)!;
            _validator.ValidateAndThrow(parsedInput);
            return true;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for {TypeName}", 
                nameof(TransactionOperationInput));
            
            parsedInput = null!;
            return false;
        }
    }

    public TransactionOperation Convert(TransactionOperationInput transactionOperationInput, Dictionary<string, object>? providedValues = null)
    {
        if (transactionOperationInput.Arguments is not null && !ValidateArguments(transactionOperationInput, providedValues))
        {
            // TODO: Consider adding more info here for debugging purposes
            _logger.LogInformation("Failed trying to validate arguments for {OperationId}", transactionOperationInput.Id);
        }
        
        return transactionOperationInput.Type switch
        {
            OperationType.Http => ConvertToHttpOperation(transactionOperationInput, providedValues),
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    private static bool ValidateArguments(TransactionOperationInput transactionOperationInput, IReadOnlyDictionary<string, object> args)
    {
        return transactionOperationInput.Arguments!
            .Where(argument => argument.Required)
            .All(requiredArgument => args.ContainsKey(requiredArgument.Name));
    }

    private TransactionOperation ConvertToHttpOperation(TransactionOperationInput input, Dictionary<string,object>? providedValues)
    {
        Action<HttpRequestMessage> func = httpRequest =>
        {
            httpRequest.Method = GetHttpMethod(input);
            
            httpRequest.Headers.Clear();
            if (input.Headers is not null)
            {
                foreach (var header in input.Headers)
                {
                    // TODO: headers.Add does not have an overload for (string, string), expects a list of values
                    var _ = httpRequest.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
            }

            if (input.Payload?.Type is not PayloadType.Json)
            {
                // TODO: do we need to implement this?
                throw new NotImplementedException("Non-JSON payloads are not supported");
            }

            JsonNode? payload = null;
            if (input.Payload is not null)
            {
                payload = ResolvePayload(input.Payload, input.Arguments, providedValues);
                httpRequest.Content = new StringContent(
                    JsonSerializer.Serialize(payload,  new JsonSerializerOptions() { WriteIndented = true}),
                    Encoding.UTF8, 
                    GetContentType(input.Payload.Type));
            }
                
            var uriBuilder = new UriBuilder(ResolveUrl(input.Url, payload, input.Arguments, providedValues));
            
            if (input.QueryParameters is not null)
            {
                var queryString = string.Join('&', input.QueryParameters.Select(qp => $"{qp.Key}={qp.Value}"));
                var queryStringParsed = HttpUtility.ParseQueryString(queryString);
                uriBuilder.Query = queryStringParsed.ToString();
            }

            httpRequest.RequestUri = uriBuilder.Uri;
        };

        return new TransactionOperation() { PrepareRequestMessage = func };
    }

    private string ResolveUrl(string? inputUrl, 
        JsonNode payload, 
        Argument[]? arguments,
        Dictionary<string, object>? providedValues)
    {
        var resolvedUrl = _urlPathArgumentRegex.Replace(inputUrl, (match) =>
        {
            var variableName = match.Groups[1].Value;
            var argument = arguments!.Single(a => a.Name == variableName);
            var valueHasBeenProvided = providedValues!.TryGetValue(variableName, out var value);
            if (!valueHasBeenProvided && !argument.Required)
            {
                // TODO: probably doesnt make sense to generate objects etc here for the URL path
                value = GenerateRandomValue(argument);
            }

            // TODO: it should not be necessary to 'pretty-print' it here, but maybe url-encode is needed?
            return value.ToString();
        });
        
        resolvedUrl = _urlPathReferenceRegex.Replace(resolvedUrl, (match) =>
        {
            var variableName = match.Groups[1].Value;

            try
            {
                var currentNode = variableName
                    .Split(".")
                    .Aggregate(payload, (current, part) => current[part]);

                // TODO: Do we need to support optional payload references?
                return currentNode.GetValue<string>();
            }
            catch (Exception exception)
            {
                _logger.LogWarning(exception,
                    "Failed trying to reference payload property in the URL. URL: {URL}, property referenced: {Property}", 
                    resolvedUrl,
                    variableName);
                
                throw;
            }
        });

        return resolvedUrl;
    }

    private JsonNode ResolvePayload(Payload inputPayload, Argument[]? arguments, Dictionary<string,object>? providedValues)
    {
        var payloadContentText  = ((JsonElement)inputPayload.Content).GetRawText();
        var resolvedPayloadText = _payloadArgumentRegex.Replace(payloadContentText, (match) =>
        {
            var variableName = match.Groups[1].Value;
            var argument = arguments!.Single(a => a.Name == variableName);
            var valueHasBeenProvided = providedValues!.TryGetValue(variableName, out var value);
            if (!valueHasBeenProvided && !argument.Required)
            {
                value = GenerateRandomValue(argument);
            }

            return PrintArgument(argument, value);
        });
        
        return JsonSerializer.Deserialize<JsonNode>(resolvedPayloadText)!;
    }

    // TODO: totally random 
    private object? GenerateRandomValue(Argument argument)
    {
        return argument.Type switch
        {
            ArgumentType.String => "random-string",
            ArgumentType.Number => 42,
            ArgumentType.Object => new { a =  5},
            ArgumentType.Array => new[] { 1, 2, 3},
            ArgumentType.Boolean => false,
            ArgumentType.Null => null,
            _ => throw new ArgumentOutOfRangeException()
        };
    }


    private static string GetContentType(PayloadType payloadType)
    {
        return payloadType switch
        {
            PayloadType.Json => "application/json",
            _ => throw new ArgumentOutOfRangeException(nameof(payloadType), payloadType, null)
        };
    }
    
    private HttpMethod GetHttpMethod(TransactionOperationInput input)
    {
        return input.HttpMethod switch
        {
            Models.Input.HttpMethod.Get => HttpMethod.Get,
            Models.Input.HttpMethod.Post => HttpMethod.Post,
            Models.Input.HttpMethod.Put => HttpMethod.Put,
            Models.Input.HttpMethod.Patch => HttpMethod.Patch,
            Models.Input.HttpMethod.Delete => HttpMethod.Delete,
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    // TODO: ugly AF, no support for arrays of different types etc.
    private string PrintArgument(Argument argument, object providedValue)
    {
        try
        {
            return argument.Type switch
            {
                ArgumentType.String => $"\"{providedValue}\"",
                ArgumentType.Number => decimal.Parse(providedValue.ToString()).ToString(),
                ArgumentType.Object => JsonSerializer.Serialize(providedValue),
                ArgumentType.Array =>
                    "[" +
                    $"{string.Join(", ", ((IEnumerable)providedValue)
                        .Cast<object>()
                        .Select(x => x.ToString()))} " +
                    "]",
                ArgumentType.Boolean => providedValue.ToString(),
                ArgumentType.Null => "null",
                _ => throw new ArgumentOutOfRangeException()
            };
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, 
                "Failed trying to print argument {ArgumentName} for provided value: {ProvidedValue}", 
                argument.Name, 
                providedValue.ToString());
            
            throw;
        }
    }
}
