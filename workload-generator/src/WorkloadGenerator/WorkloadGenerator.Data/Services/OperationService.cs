using System.Collections;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.Web;
using Microsoft.Extensions.Logging;
using Utilities;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Operation.Sleep;
using HttpMethod = System.Net.Http.HttpMethod;

namespace WorkloadGenerator.Data.Services;

public class OperationService : IOperationService
{
    private readonly ILogger<OperationService> _logger;

    // for payloads we need to replace the quotes since we might be replacing this string with a number/array/null/boolean
    // e.g. "key1": "{{arg1}}" => "key1": false
    // the reason we need the quotes in the first place, is because `"key1": @arg1` is not a valid JSON
    private readonly Regex _argumentReplaceRegex = new("\"{{([^}]*)}}\"");

    // for url paths we do not need expect any quotes so we just need to replace the `{{arg-name}}`
    private readonly Regex _stringArgumentReplaceRegex = new("{{([^}]*)}}");


    // for url paths we do not need expect any quotes so we just need to replace the `@@arg-name@@`
    private readonly Regex _urlPathReferenceRegex = new("@@([^}]*)@@");

    public OperationService(ILogger<OperationService> logger)
    {
        _logger = logger;
    }

    private readonly JsonSerializerOptions _jsonSerializerOptions = SerializerUtils.GetGlobalJsonSerializerOptions();

    public bool TryParseInput(string json, out IOperationUnresolved unresolvedInput)
    {
        unresolvedInput = null!;
        try
        {
            var baseInput = JsonSerializer.Deserialize<OperationInputBase>(json, _jsonSerializerOptions);
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

    private IOperationUnresolved Parse<T>(string json) where T : IOperationUnresolved
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

    public bool TryResolve(IOperationUnresolved unresolvedInput, Dictionary<string, object> providedValues,
        out IOperationResolved resolvedInput)
    {
        if (!Utilities.ValidateArguments(unresolvedInput.Arguments, providedValues))
        {
            resolvedInput = null!;
            return false;
        }

        providedValues = Utilities.AddDynamicValues(unresolvedInput.DynamicVariables, providedValues);

        resolvedInput = ResolveInternal(unresolvedInput, providedValues);
        return true;
    }

    public bool TryConvertToExecutable(IOperationResolved resolvedInput,
        out OperationExecutableBase operationBaseExecutable)
    {
        try
        {
            operationBaseExecutable = resolvedInput switch
            {
                HttpOperationInputResolved httpOperationInputResolved => ConvertToExecutableHttpOperation(
                    httpOperationInputResolved),
                SleepOperationInputResolved sleepOperationInputResolved => ConvertToExecutableSleepOperation(
                    sleepOperationInputResolved),
                _ => throw new ArgumentOutOfRangeException(nameof(resolvedInput))
            };

            return true;
        }
        catch (Exception e)
        {
            _logger.LogWarning(e, "Failed converting to executable");
            operationBaseExecutable = null!;
            return false;
        }
    }

    private OperationExecutableBase ConvertToExecutableSleepOperation(SleepOperationInputResolved input)
    {
        var duration = decimal.ToDouble(input.Duration);
        var timespan = input.Units switch
        {
            TimeSpanType.Milliseconds => TimeSpan.FromMilliseconds(duration),
            TimeSpanType.Seconds => TimeSpan.FromSeconds(duration),
            TimeSpanType.Minutes => TimeSpan.FromMinutes(duration),
            _ => throw new ArgumentOutOfRangeException()
        };

        return new SleepOperationExecutable
        {
            Sleep = () => Task.Delay(timespan)
        };
    }

    private OperationExecutableBase ConvertToExecutableHttpOperation(HttpOperationInputResolved input)
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
                    JsonSerializer.Serialize(jsonPayload.Content, _jsonSerializerOptions),
                    Encoding.UTF8,
                    "application/json");
            }
        };

        return new HttpOperationExecutable() { PrepareRequestMessage = func };
    }

    private IOperationResolved ResolveInternal(
        IOperationUnresolved unresolvedInput,
        Dictionary<string, object> providedValues)
    {
        return unresolvedInput switch
        {
            HttpOperationInputUnresolved httpOperation => ResolveHttpOperation(httpOperation, providedValues),
            SleepOperationInputUnresolved sleepOperation => ResolveSleepOperation(sleepOperation, providedValues),
            _ => throw new ArgumentOutOfRangeException(nameof(unresolvedInput))
        };
    }

    private IOperationResolved ResolveHttpOperation(HttpOperationInputUnresolved unresolvedInput,
        Dictionary<string, object> providedValues)
    {
        var _ = TryResolveRequestPayload(unresolvedInput, providedValues, out var resolvedPayload);
        return new HttpOperationInputResolved()
        {
            Headers = ResolveHeaders(unresolvedInput.Headers, unresolvedInput.Arguments, providedValues),
            HttpMethod = unresolvedInput.HttpMethod,
            RequestPayload = resolvedPayload,
            TemplateId = unresolvedInput.TemplateId,
            QueryParameters = unresolvedInput.QueryParameters,
            Type = unresolvedInput.Type,
            // TODO: ResponsePayload = ResolveResponsePayload(unresolvedInput.ResponsePayload),
            Url = ResolveUrl(unresolvedInput, resolvedPayload, providedValues)
        };
    }

    private IOperationResolved ResolveSleepOperation(SleepOperationInputUnresolved sleepOperation,
        Dictionary<string, object> providedValues)
    {
        Decimal duration;
        if (sleepOperation.Arguments is { Length: > 0 })
        {
            try
            {
                duration = Decimal.Parse(ResolveParameterizedString(sleepOperation.Duration, sleepOperation.Arguments, providedValues));
            }
            catch (Exception exception)
            {
                _logger.LogWarning(exception,
                    $"Failed trying resolve duration: {sleepOperation.Duration} for sleep operation.");
                throw;
            }
        }
        else
        {
            duration = Decimal.Parse(sleepOperation.Duration);
        }

        return new SleepOperationInputResolved()
        {
            Duration = duration,
            TemplateId = sleepOperation.TemplateId,
            Units = sleepOperation.Units
        };
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

        if (payload is null)
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
        Dictionary<string, object> providedValues,
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


                // resolve path on argument, example {{@@item1[0].price@@}}
                if (variableName.StartsWith("@@"))
                {
                    var wholePath = variableName.Replace("@", "");
                    var argumentName = string.Concat(wholePath.TakeWhile((c) => c != '[' && c != '.'));
                    var argumentPath = string.Concat(wholePath.Skip(argumentName.Length));

                    var rootElement = inputUnresolved.Arguments!.SingleOrDefault(a => a.Name == argumentName);
                    if (rootElement is not null)
                    {
                        var jsonDocument = JsonDocument.Parse(((JsonElement)providedValues[argumentName]).GetRawText());
                        var jsonElement = jsonDocument.SelectElement("$" + argumentPath);
                        if (jsonElement.HasValue)
                        {
                            return jsonElement.Value.GetRawText();
                        }
                    }
                }

                if (inputUnresolved.Arguments is not null)
                {
                    var argument = inputUnresolved.Arguments.SingleOrDefault(a => a.Name == variableName);
                    if (argument is not null)
                    {
                        return PrintArgument(argument, providedValues[variableName]);
                    }
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
                ArgumentType.Guid => $"\"{providedValue}\"",
                ArgumentType.Number => decimal.Parse(providedValue.ToString()).ToString(),
                ArgumentType.Object => JsonSerializer.Serialize(providedValue),
                ArgumentType.Array =>
                    "[" +
                    $"{string.Join(", ", ((IEnumerable)providedValue).Cast<object>().Select(x => x.ToString()))} " +
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
