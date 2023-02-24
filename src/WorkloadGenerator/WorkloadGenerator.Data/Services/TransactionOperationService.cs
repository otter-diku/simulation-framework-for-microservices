using System.Collections;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Web;
using FluentValidation;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models;
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

    private readonly TransactionOperationInputBaseValidator _baseValidator = new();
    

    private static bool ValidateArguments(TransactionOperationInputUnresolved transactionOperationInput, IReadOnlyDictionary<string, object> args)
    {
        return transactionOperationInput.Arguments!
            .Where(argument => argument.Required)
            .All(requiredArgument => args.ContainsKey(requiredArgument.Name));
    }

    private TransactionOperationInputResolved<T> ResolveInternal<T>(TransactionOperationInputUnresolved unresolvedInput,
        Dictionary<string, object>? providedValues = null)
    {
        var resolvedPayload = ResolvePayload<T>(unresolvedInput.Payload, unresolvedInput.Arguments, providedValues);
        return new TransactionOperationInputResolved<T>()
        {
            Headers = unresolvedInput.Headers,
            Payload = resolvedPayload,
            HttpMethod = unresolvedInput.HttpMethod,
            Id = unresolvedInput.Id,
            QueryParameters = unresolvedInput.QueryParameters,
            Type = unresolvedInput.Type,
            Url = ResolveUrl(unresolvedInput.Url, resolvedPayload, unresolvedInput.Arguments, providedValues)
        };
    }

    private Type getType(PayloadType payloadType)
    {
        return typeof(JsonNode);
    }
    
    private TransactionOperation ConvertToHttpOperation<T>(TransactionOperationInputResolved<T> input)
    {
        Action<HttpRequestMessage> func = httpRequest =>
        {
            httpRequest.Method = GetHttpMethod(input.HttpMethod);
            
            httpRequest.Headers.Clear();
            if (input.Headers is not null)
            {
                foreach (var header in input.Headers)
                {
                    // TODO: headers.Add does not have an overload for (string, string), expects a list of values
                    var _ = httpRequest.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
            }

            if (input.Payload is not JsonNode jsonNode)
            {
                // TODO: do we need to implement this?
                throw new NotImplementedException("Non-JSON payloads are not supported");
            }

            JsonNode? payload = null;
            if (input.Payload is not null)
            {
                    httpRequest.Content = new StringContent(
                        JsonSerializer.Serialize(jsonNode,  new JsonSerializerOptions() { WriteIndented = true}),
                        Encoding.UTF8,
                        "application/json");
            }

            var uriBuilder = new UriBuilder(input.Url);
            
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

    private string ResolveUrl<T>(string? inputUrl, 
        T payload, 
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
        
        if (payload is not JsonNode jsonNode)
        {
            return resolvedUrl;
        }

        resolvedUrl = _urlPathReferenceRegex.Replace(resolvedUrl, (match) =>
        {
            var variableName = match.Groups[1].Value;

            try
            {

                var finalNode = variableName
                    .Split(".")
                    .Aggregate(jsonNode, (current, part) => current[part]);

                return finalNode.GetValue<string>();
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

    private T ResolvePayload<T>(Payload inputPayload, Argument[]? arguments, Dictionary<string,object>? providedValues)
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
        
        return JsonSerializer.Deserialize<T>(resolvedPayloadText);
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
    
    private HttpMethod GetHttpMethod(Models.HttpMethod? method)
    {
        return method switch
        {
            Models.HttpMethod.Get => HttpMethod.Get,
            Models.HttpMethod.Post => HttpMethod.Post,
            Models.HttpMethod.Put => HttpMethod.Put,
            Models.HttpMethod.Patch => HttpMethod.Patch,
            Models.HttpMethod.Delete => HttpMethod.Delete,
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

    public bool TryParseInput(string json, out TransactionOperationInputUnresolved parsedInput)
    {
        try
        {
            parsedInput = JsonSerializer.Deserialize<TransactionOperationInputUnresolved>(json, _jsonSerializerOptions)!;
            _baseValidator.ValidateAndThrow(parsedInput);
            return true;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for {TypeName}", 
                nameof(TransactionOperationInputUnresolved));
            
            parsedInput = null!;
            return false;
        }        
    }

    public TransactionOperationInputResolved<T> Resolve<T>(TransactionOperationInputUnresolved transactionOperationInputUnresolved,
        Dictionary<string, object>? providedValues = null)
    {
        if (transactionOperationInputUnresolved.Arguments is not null && !ValidateArguments(transactionOperationInputUnresolved, providedValues))
        {
            // TODO: Consider adding more info here for debugging purposes
            _logger.LogInformation("Failed trying to validate arguments for {OperationId}", transactionOperationInputUnresolved.Id);
        }

        return ResolveInternal<T>(transactionOperationInputUnresolved, providedValues);
    }

    public TransactionOperation Convert<T>(TransactionOperationInputResolved<T> resolvedInput)
    {
        return resolvedInput.Type switch
        {
            OperationType.Http => ConvertToHttpOperation(resolvedInput),
            _ => throw new ArgumentOutOfRangeException()
        };
    }
}
