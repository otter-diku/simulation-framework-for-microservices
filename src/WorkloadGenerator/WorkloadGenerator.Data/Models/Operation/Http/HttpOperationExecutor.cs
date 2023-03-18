using System.Text.Json;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Models.Operation.Http;

public class HttpOperationExecutor : IOperationExecutor
{
    private readonly ILogger<HttpOperationExecutor> _logger;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ITransactionOperationService _transactionOperationService;

    public HttpOperationExecutor(ILogger<HttpOperationExecutor> logger, 
        IHttpClientFactory httpClientFactory,
        ITransactionOperationService transactionOperationService)
    {
        _logger = logger;
        _httpClientFactory = httpClientFactory;
        _transactionOperationService = transactionOperationService;
    }

    public bool CanHandle(ITransactionOperationResolved resolved)
    {
        return resolved is HttpOperationInputResolved;
    }

    public async Task<Dictionary<string, object>> Execute(ITransactionOperationResolved resolved)
    {
        if (resolved is not HttpOperationInputResolved httpOperation)
        {
            throw new ArgumentOutOfRangeException();
        }

        if (!_transactionOperationService.TryConvertToExecutable(httpOperation, out var executable))
        {
            throw new Exception();
        }

        if (executable is not HttpOperationTransactionExecutable httpExecutable || httpExecutable is null) 
        {
            throw new Exception();
        }
        
        var client = _httpClientFactory.CreateClient();
        var message = new HttpRequestMessage();
        
        httpExecutable.PrepareRequestMessage!(message);

        var response = await client.SendAsync(message);

        return await ExtractReturnValues(httpOperation, response);
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
            throw new NotImplementedException();
            // TODO:
            // var extracted = await ResolveResponseHeaders(httpOperationResponseInput.Payload, responseMessage);
            // foreach (var kv in extracted)
            // {
            //     extractedValues.Add(kv.Key, kv.Value);
            // }
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
                JsonSerializer.Serialize(httpOperationResponsePayloadInput),
                content
            );
            throw;
        }
    }
}