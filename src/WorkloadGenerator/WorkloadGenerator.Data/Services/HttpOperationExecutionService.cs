using System.Text.Json;
using Microsoft.Extensions.Logging;
using Utilities;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;

namespace WorkloadGenerator.Data.Services;

public class HttpOperationExecutionService : IOperationExecutionService
{
    private readonly ILogger<HttpOperationExecutionService> _logger;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly IOperationService _operationService;

    private readonly JsonSerializerOptions _jsonSerializerOptions = SerializerUtils.GetGlobalJsonSerializerOptions();
    
    public HttpOperationExecutionService(ILogger<HttpOperationExecutionService> logger,
        IHttpClientFactory httpClientFactory,
        IOperationService operationService)
    {
        _logger = logger;
        _httpClientFactory = httpClientFactory;
        _operationService = operationService;
    }
    
    public bool CanHandle(IOperationUnresolved operation) => operation.Type == OperationType.Http;
    
    public async Task<Dictionary<string, object>> Execute(IOperationUnresolved unresolved, Dictionary<string, object> providedValues)
    {
        if (unresolved is not HttpOperationInputUnresolved httpOperationUnresolved)
        {
            _logger.LogWarning("Invalid execution service picked");
            return providedValues;
        }
        
        var didResolve = _operationService.TryResolve(httpOperationUnresolved, providedValues, out var resolved);
        if (!didResolve)
        {
            _logger.LogWarning("Failed to resolve operation");
            return providedValues;
        }
        
        if (resolved is not HttpOperationInputResolved httpOperationResolved)
        {
            _logger.LogWarning("Invalid type of resolved operation");
            return providedValues;
        }

        if (!_operationService.TryConvertToExecutable(httpOperationResolved, out var executable))
        {
            _logger.LogWarning("Failed to convert operation to executable");
            return providedValues;
        }

        if (executable is not HttpOperationExecutable httpExecutable) 
        {
            _logger.LogWarning("Invalid executable type");
            return providedValues;
        }
        
        var client = _httpClientFactory.CreateClient();
        var message = new HttpRequestMessage();
        
        httpExecutable.PrepareRequestMessage!(message);

        var response = await client.SendAsync(message);

        return await ExtractReturnValues(httpOperationResolved, response);
    }
    
    private async Task<Dictionary<string, object>> ExtractReturnValues(
        HttpOperationInputResolved operation,
        object result)
    {
        if (operation.Response is not null)
        {
            return result switch
            {
                HttpResponseMessage responseMessage => await ExtractReturnValuesFromHttpMessage(operation.Response,
                    responseMessage),
                _ => throw new ArgumentOutOfRangeException(nameof(result), result, null)
            };
        }

        return new Dictionary<string, object>();
    }

    private async Task<Dictionary<string, object>> ExtractReturnValuesFromHttpMessage(
        HttpOperationResponseInput? httpOperationResponseInput,
        HttpResponseMessage responseMessage)
    {
        var extractedValues = new Dictionary<string, object>();

        if (httpOperationResponseInput?.Payload is not null)
        {
            var extracted = await ResolveResponsePayload(httpOperationResponseInput.Payload, responseMessage);
            foreach (var kv in extracted)
            {
                extractedValues.Add(kv.Key, kv.Value);
            }
        }

        if (httpOperationResponseInput?.Headers is not null)
        {
            // TODO: Add support for headers
            throw new NotImplementedException();
        }

        return extractedValues;
    }

    private async Task<Dictionary<string, object>> ResolveResponsePayload(
        HttpOperationResponsePayloadInput httpOperationResponsePayloadInput, HttpResponseMessage responseMessage)
    {
        var extractedValues = new Dictionary<string, object>();

        return httpOperationResponsePayloadInput.Type switch
        {
            HttpPayloadType.Json => await ExtractedReturnValuesFromJsonPayload(httpOperationResponsePayloadInput,
                responseMessage, extractedValues),
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    private async Task<Dictionary<string, object>> ExtractedReturnValuesFromJsonPayload(
        HttpOperationResponsePayloadInput httpOperationResponsePayloadInput,
        HttpResponseMessage responseMessage,
        Dictionary<string, object> extractedValues)
    {
        string? content = null;
        try
        {
            content = await responseMessage.Content.ReadAsStringAsync();
            var jsonDocument = JsonDocument.Parse(content);

            foreach (var rv in httpOperationResponsePayloadInput.ReturnValues)
            {
                var obj = jsonDocument.RootElement.SelectElement(rv.Value);
                if (obj.HasValue)
                {
                    extractedValues.Add(rv.Key, obj.Value);
                }
            }

            return extractedValues;
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception,
                "Failed trying to extract return value from payload. Input: {Input}, response message payload: {ResponseMessagePayload}",
                JsonSerializer.Serialize(httpOperationResponsePayloadInput, _jsonSerializerOptions),
                content
            );
            throw;
        }
    }
}