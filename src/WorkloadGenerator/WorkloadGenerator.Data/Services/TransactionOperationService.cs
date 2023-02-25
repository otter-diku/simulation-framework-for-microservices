using System.Collections;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Web;
using AutoFixture;
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

    private readonly TransactionOperationInputBaseValidator _baseValidator = new();
    

    private static bool ValidateArguments(TransactionOperationInputUnresolved transactionOperationInput, IReadOnlyDictionary<string, object> args)
    {
        return transactionOperationInput.Arguments!
            .All(requiredArgument => args.ContainsKey(requiredArgument.Name));
    }
    
    private TransactionOperationInputResolved ResolveInternal(TransactionOperationInputUnresolved unresolvedInput,
        Dictionary<string, object>? providedValues = null)
    {
        return new TransactionOperationInputResolved()
        {
            Headers = ResolveHeaders(unresolvedInput.Headers, unresolvedInput.Arguments, providedValues),
            HttpMethod = unresolvedInput.HttpMethod,
            Id = unresolvedInput.Id,
            QueryParameters = unresolvedInput.QueryParameters,
            Type = unresolvedInput.Type,
            Url = ResolveUrl(unresolvedInput.Url, unresolvedInput.Arguments, providedValues)
        };
    }

    private TransactionOperationInputResolved<T> ResolveInternal<T>(TransactionOperationInputUnresolved unresolvedInput,
        Dictionary<string, object>? providedValues = null)
    {
        var resolvedPayload = ResolvePayload<T>(unresolvedInput.Payload, unresolvedInput.Arguments, unresolvedInput.DynamicVariables, providedValues);
        return new TransactionOperationInputResolved<T>()
        {
            Headers = ResolveHeaders(unresolvedInput.Headers, unresolvedInput.Arguments, providedValues),
            Payload = resolvedPayload,
            HttpMethod = unresolvedInput.HttpMethod,
            Id = unresolvedInput.Id,
            QueryParameters = unresolvedInput.QueryParameters,
            Type = unresolvedInput.Type,
            Url = ResolveUrl(unresolvedInput.Url, resolvedPayload, unresolvedInput.Arguments, providedValues)
        };
    }

    private List<Header> ResolveHeaders(
        List<Header> unresolvedInputHeaders, 
        Argument[]? arguments, 
        Dictionary<string, object>? providedValues)
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
    
    private TransactionOperation ConvertToHttpOperation(TransactionOperationInputResolved input)
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

    
    private string ResolveUrl(string? inputUrl, 
        Argument[]? arguments,
        Dictionary<string, object>? providedValues)
    {
        return ResolveParameterizedString(inputUrl, arguments, providedValues);
    }

    private string ResolveUrl<T>(string? inputUrl, 
        T payload, 
        Argument[]? arguments,
        Dictionary<string, object>? providedValues)
    {
        var resolvedUrl = ResolveParameterizedString(inputUrl, arguments, providedValues);
        
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

    private string ResolveParameterizedString(
        string? unresolvedInput, 
        Argument[] arguments, 
        Dictionary<string, object> providedValues
    )
    {
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

    private T ResolvePayload<T>(Payload inputPayload, Argument[]? arguments, DynamicVariable[]? dynamicVariables,
        Dictionary<string,object>? providedValues)
    {
        var payloadContentText  = ((JsonElement)inputPayload.Content).GetRawText();
        var resolvedPayloadText = _argumentReplaceRegex.Replace(payloadContentText, (match) =>
        {
            var variableName = match.Groups[1].Value;
            var argument = arguments!.SingleOrDefault(a => a.Name == variableName);
            if (argument is not null)
            {
                return PrintArgument(argument, providedValues[variableName]);
            }

            var dynamicVariable = dynamicVariables.Single(v => v.Name == variableName);
            return PrintDynamicVariable(dynamicVariable, providedValues[variableName]);
        });
        
        return JsonSerializer.Deserialize<T>(resolvedPayloadText);
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
        providedValues ??= new Dictionary<string, object>();
        
        if (transactionOperationInputUnresolved.DynamicVariables is not null)
        {
            var concreteVariables = GenerateDynamicVariables(transactionOperationInputUnresolved.DynamicVariables);
            foreach (var v in concreteVariables)
            {
                providedValues.Add(v.Key, v.Value);
            }    
        }

        return ResolveInternal<T>(transactionOperationInputUnresolved, providedValues);
    }

    public TransactionOperationInputResolved Resolve(TransactionOperationInputUnresolved transactionOperationInputUnresolved,
        Dictionary<string, object>? providedValues = null)
    {
        if (transactionOperationInputUnresolved.Arguments is not null && !ValidateArguments(transactionOperationInputUnresolved, providedValues))
        {
            // TODO: Consider adding more info here for debugging purposes
            _logger.LogInformation("Failed trying to validate arguments for {OperationId}", transactionOperationInputUnresolved.Id);
        }
        providedValues ??= new Dictionary<string, object>();
        
        if (transactionOperationInputUnresolved.DynamicVariables is not null)
        {
            var concreteVariables = GenerateDynamicVariables(transactionOperationInputUnresolved.DynamicVariables);
            foreach (var v in concreteVariables)
            {
                providedValues.Add(v.Key, v.Value);
            }    
        }
        
        return ResolveInternal(transactionOperationInputUnresolved, providedValues);
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

    public TransactionOperation Convert<T>(TransactionOperationInputResolved<T> resolvedInput)
    {
        return resolvedInput.Type switch
        {
            OperationType.Http => ConvertToHttpOperation(resolvedInput),
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    public TransactionOperation Convert(TransactionOperationInputResolved resolvedInput)
    {
        return resolvedInput.Type switch
        {
            OperationType.Http => ConvertToHttpOperation(resolvedInput),
            _ => throw new ArgumentOutOfRangeException()
        };
    }
}
