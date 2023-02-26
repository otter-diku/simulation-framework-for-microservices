using System.Collections;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Web;
using AutoFixture;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Operation.Sleep;
using HttpMethod = System.Net.Http.HttpMethod;

namespace WorkloadGenerator.Data.Services;

public class TransactionOperationService : ITransactionOperationService
{
    private readonly ILogger<TransactionOperationService> _logger;

    // for payloads we need to replace the quotes since we might be replacing this string with a number/array/null/boolean
    // e.g. "key1": "{{arg1}}" => "key1": false 
    // the reason we need the quotes in the first place, is because `"key1": @arg1` is not a valid JSON
    private readonly Regex _argumentReplaceRegex = new("\"{{([^}]*)}}\"");
    
    // for url paths we do not need expect any quotes so we just need to replace the `{{arg-name}}`
    private readonly Regex _stringArgumentReplaceRegex = new("{{([^}]*)}}");
    
    
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

    public bool TryParseInput(string json, out ITransactionOperationUnresolved unresolvedInput)
    {
        unresolvedInput = null!;
        try
        {
            var baseInput = JsonSerializer.Deserialize<TransactionOperationInputBase>(json, _jsonSerializerOptions);
            if (baseInput is null)
            {
                return false;
            }
            
            switch (baseInput.Type)
            {
                case OperationType.Http:
                {
                    unresolvedInput = Parse<HttpOperationInputUnresolved>(json);
                    return true;
                }
                case OperationType.Sleep:
                {
                    unresolvedInput = Parse<SleepOperationInputUnresolved>(json);
                    return true;
                }
                default:
                    throw new ArgumentOutOfRangeException();
            }
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for operation");
            
            return false;
        }
    }

    private ITransactionOperationUnresolved Parse<T>(string json) where T : ITransactionOperationUnresolved
    {
        var operationUnresolved =
            JsonSerializer.Deserialize<T>(json, _jsonSerializerOptions);

        if (operationUnresolved is null)
        {
            throw new ArgumentException($"Cannot parse string into {typeof(T)} - invalid JSON provided");
        }

        operationUnresolved.ValidateAndThrow();

        return operationUnresolved;
    }

    public bool TryResolve(ITransactionOperationUnresolved unresolvedInput, Dictionary<string, object>? providedValues,
        out ITransactionOperationResolved resolvedInput)
    {
        if (!ValidateArguments(unresolvedInput, providedValues))
        {
            resolvedInput = null!;
            return false;
        }
        
        providedValues = AddDynamicValues(unresolvedInput, providedValues ?? new Dictionary<string, object>());

        resolvedInput = ResolveInternal(unresolvedInput, providedValues);
        return true;
    }

    private Dictionary<string, object> AddDynamicValues(ITransactionOperationUnresolved unresolvedInput, Dictionary<string, object> providedValues)
    {
        if (unresolvedInput.DynamicVariables is null)
        {
            return providedValues;
        }
        
        var concreteVariables = GenerateDynamicVariables(unresolvedInput.DynamicVariables);
        foreach (var kv in concreteVariables)
        {
            providedValues.Add(kv.Key, kv.Value);
        }

        return providedValues;
    }

    public bool TryConvertToExecutable(ITransactionOperationResolved resolvedInput,
        out TransactionOperationExecutableBase transactionOperationbaseExecutable)
    {
        try
        {
            transactionOperationbaseExecutable = resolvedInput switch
            {
                HttpOperationInputResolved httpOperationInputResolved => ConvertToExecutableHttpOperation(httpOperationInputResolved),
                SleepOperationInputResolved sleepOperationInputResolved => ConvertToExecutableSleepOperation(sleepOperationInputResolved),
                _ => throw new ArgumentOutOfRangeException(nameof(resolvedInput))
            };
            
            return true;
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Failed converting to executable");
            transactionOperationbaseExecutable = null!;
            return false;
        }
    }

    private TransactionOperationExecutableBase ConvertToExecutableSleepOperation(SleepOperationInputResolved input)
    {
        var duration = decimal.ToDouble(input.Duration);
        var timespan = input.Units switch
        {
            TimeSpanType.Milliseconds => TimeSpan.FromMilliseconds(duration),
            TimeSpanType.Seconds => TimeSpan.FromSeconds(duration),
            TimeSpanType.Minutes => TimeSpan.FromMinutes(duration),
            _ => throw new ArgumentOutOfRangeException()
        };

        return new SleepOperationTransactionExecutable
        {
            Sleep = () => Task.Delay(timespan)
        };
    }

    private TransactionOperationExecutableBase ConvertToExecutableHttpOperation(HttpOperationInputResolved input)
    {
        Action<HttpRequestMessage> func = httpRequest =>
        {
            httpRequest.Method = GetHttpMethod(input.HttpMethod);

            var uriBuilder = new UriBuilder(input.Url);
            if (input.QueryParameters is not null)
            {
                var queryString = string.Join('&', input.QueryParameters.Select(qp => $"{qp.Key}={qp.Value}"));
                var queryStringParsed = HttpUtility.ParseQueryString(queryString);
                uriBuilder.Query = queryStringParsed.ToString();
            }
            httpRequest.RequestUri = uriBuilder.Uri;

            httpRequest.Headers.Clear();
            if (input.Headers is not null)
            {
                foreach (var header in input.Headers)
                {
                    // TODO: headers.Add does not have an overload for (string, string), expects a list of values
                    var _ = httpRequest.Headers.TryAddWithoutValidation(header.Key, header.Value);
                }
            }
            
            if (input.RequestPayload is null)
            {
                return;
            }

            if (input.RequestPayload is not JsonPayloadResolved jsonPayload)
            {
                // TODO: do we need to implement this?
                throw new NotImplementedException("Non-JSON payloads are not supported");
            }

            if (input.RequestPayload is not null)
            {
                httpRequest.Content = new StringContent(
                    JsonSerializer.Serialize(jsonPayload.Content,  new JsonSerializerOptions() { WriteIndented = true}),
                    Encoding.UTF8,
                    "application/json");
            }
        };

        return new HttpOperationTransactionExecutable() { PrepareRequestMessage = func };
    }


    private static bool ValidateArguments(ITransactionOperationUnresolved unresolvedInput, IReadOnlyDictionary<string, object>? providedValues)
    {
        return unresolvedInput.Arguments is null ||
               unresolvedInput.Arguments.All(requiredArgument => providedValues?.ContainsKey(requiredArgument.Name) ?? false);
    }
    
    private ITransactionOperationResolved ResolveInternal(
        ITransactionOperationUnresolved unresolvedInput,
        Dictionary<string, object> providedValues)
    {
        return unresolvedInput switch
        {
            HttpOperationInputUnresolved httpOperation => ResolveHttpOperation(httpOperation, providedValues),
            SleepOperationInputUnresolved sleepOperation => ResolveSleepOperation(sleepOperation, providedValues),
            _ => throw new ArgumentOutOfRangeException(nameof(unresolvedInput))
        };
    }

    private ITransactionOperationResolved ResolveHttpOperation(HttpOperationInputUnresolved unresolvedInput, Dictionary<string, object> providedValues)
    {
        var _ = TryResolveRequestPayload(unresolvedInput, providedValues, out var resolvedPayload);
        return new HttpOperationInputResolved()
        {
            Headers = ResolveHeaders(unresolvedInput.Headers, unresolvedInput.Arguments, providedValues),
            HttpMethod = unresolvedInput.HttpMethod,
            RequestPayload = resolvedPayload,
            Id = unresolvedInput.Id,
            QueryParameters = unresolvedInput.QueryParameters,
            Type = unresolvedInput.Type,
            // TODO: ResponsePayload = ResolveResponsePayload(unresolvedInput.ResponsePayload),
            Url = ResolveUrl(unresolvedInput, resolvedPayload, providedValues)
        };
    }
    
    private ITransactionOperationResolved ResolveSleepOperation(SleepOperationInputUnresolved sleepOperation, Dictionary<string, object> providedValues)
    {
        throw new NotImplementedException();
    }

    private List<Header>? ResolveHeaders(
        List<Header>? unresolvedInputHeaders, 
        Argument[]? arguments, 
        Dictionary<string, object> providedValues)
    {
        if (unresolvedInputHeaders is not { Count: > 0 } || arguments is not { Length: > 0 })
        {
            return unresolvedInputHeaders;
        }

        return unresolvedInputHeaders
            .Select(header => new Header()
            {
                Key = ResolveParameterizedString(header.Key, arguments, providedValues),
                Value = ResolveParameterizedString(header.Value, arguments, providedValues),
            })
            .ToList();
    }
    
    private string ResolveUrl(HttpOperationInputUnresolved unresolved,
        HttpOperationRequestPayloadResolvedBase payload,
        Dictionary<string, object>? providedValues)
    {
        var resolvedUrl = ResolveParameterizedString(unresolved.Url, unresolved.Arguments, providedValues);
        
        if (payload is null )
        {
            return resolvedUrl;
        }

        if (payload is not JsonPayloadResolved jsonPayload)
        {
            // TODO: We only support JSON payloads to be used when being referenced by other fields 
            return resolvedUrl;
        }

        resolvedUrl = _urlPathReferenceRegex.Replace(resolvedUrl, (match) =>
        {
            var variableName = match.Groups[1].Value;

            try
            {
                var finalNode = variableName
                    .Split(".")
                    .Aggregate(jsonPayload.Content, (current, part) => current[part]);

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

    private string ResolveParameterizedString(
        string? unresolvedInput, 
        Argument[] arguments, 
        IReadOnlyDictionary<string, object> providedValues
    )
    {
        if (string.IsNullOrEmpty(unresolvedInput))
        {
            return unresolvedInput;
        }

        return _stringArgumentReplaceRegex.Replace(unresolvedInput, (match) =>
        {
            try
            {
                var variableName = match.Groups[1].Value;
                
                return providedValues[variableName].ToString();
            }
            catch (InvalidOperationException invalidOperationException)
            {
                _logger.LogWarning(invalidOperationException,
                    "Failed trying to resolve input: {UnresolvedInput}, args: {Arguments}, provided values: {ProvidedValues}",
                    unresolvedInput,
                    string.Join(" | ", arguments.Select(a => $"{a.Name}, {a.Type}")),
                    string.Join(" | ", providedValues.Select(kv => $"{kv.Key}: {kv.Value}")));
                throw;
            }
        });
    }

    private bool TryResolveRequestPayload(
        HttpOperationInputUnresolved inputUnresolved,
        Dictionary<string,object> providedValues,
        out HttpOperationRequestPayloadResolvedBase resolvedPayload)
    {
        resolvedPayload = null!;

        if (inputUnresolved.RequestPayload?.Type is not HttpPayloadType.Json)
        {
            // TODO: We currently only support resolving JSON payloads
            return false;
        }

        try
        {
            var payloadContentText = ((JsonElement)inputUnresolved.RequestPayload.Content).GetRawText();
            var resolvedPayloadText = _argumentReplaceRegex.Replace(payloadContentText, (match) =>
            {
                var variableName = match.Groups[1].Value;

                var argument = inputUnresolved.Arguments!.SingleOrDefault(a => a.Name == variableName);
                if (argument is not null)
                {
                    return PrintArgument(argument, providedValues[variableName]);
                }

                var dynamicVariable = inputUnresolved.DynamicVariables?.SingleOrDefault(v => v.Name == variableName);
                if (dynamicVariable is not null)
                {
                    return PrintDynamicVariable(dynamicVariable, providedValues[variableName]);
                }

                throw new ArgumentException($"Cannot find matching arg or var for name {variableName}");
            });

            resolvedPayload = new JsonPayloadResolved() { Content = JsonNode.Parse(resolvedPayloadText) };

            return true;
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, exception.Message);
            return false;
        }
    }

    private string PrintDynamicVariable(DynamicVariable dynamicVariable, object providedValue)
    {
        try
        {
            return dynamicVariable.Type switch
            {
                DynamicVariableType.UnsignedInt => decimal.Parse(providedValue.ToString()).ToString(),
                DynamicVariableType.SignedInt => decimal.Parse(providedValue.ToString()).ToString(),
                DynamicVariableType.String => $"\"{providedValue}\"",
                DynamicVariableType.Guid => $"\"{providedValue}\"",
                _ => throw new ArgumentOutOfRangeException()
            };
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, 
                "Failed trying to print dynamic variable {DynamicVariable} for provided value: {ProvidedValue}", 
                dynamicVariable.Name, 
                providedValue.ToString());
            
            throw;
        }
        
    }

    private HttpMethod GetHttpMethod(Models.Operation.Http.HttpMethod? method)
    {
        return method switch
        {
            Models.Operation.Http.HttpMethod.Get => HttpMethod.Get,
            Models.Operation.Http.HttpMethod.Post => HttpMethod.Post,
            Models.Operation.Http.HttpMethod.Put => HttpMethod.Put,
            Models.Operation.Http.HttpMethod.Patch => HttpMethod.Patch,
            Models.Operation.Http.HttpMethod.Delete => HttpMethod.Delete,
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

    private Dictionary<string, object> GenerateDynamicVariables(DynamicVariable[] dynamicVariables)
    {
        var fixture = new Fixture();
        return dynamicVariables.ToDictionary(dynamicVar => dynamicVar.Name, dynamicVar =>
        {
            return dynamicVar.Type switch
            {
                DynamicVariableType.UnsignedInt => fixture.Create<uint>(),
                DynamicVariableType.SignedInt => fixture.Create<bool>()
                    ? fixture.Create<int>()
                    : -1 * fixture.Create<int>(),
                DynamicVariableType.String => (object) fixture.Create<string>(),
                DynamicVariableType.Guid => Guid.NewGuid(),
                _ => throw new ArgumentOutOfRangeException()
            };
        });
    }
}
