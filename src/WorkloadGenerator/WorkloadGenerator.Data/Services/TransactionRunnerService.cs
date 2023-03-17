using System.Diagnostics;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Utilities;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Operation.Sleep;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public class TransactionRunnerService
{
    private readonly ITransactionOperationService _transactionOperationService;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<TransactionRunnerService> _logger;

    public TransactionRunnerService(ITransactionOperationService transactionOperationService,
        IHttpClientFactory httpClientFactory,
        ILogger<TransactionRunnerService> logger)
    {
        _transactionOperationService = transactionOperationService;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    public async Task Run(
        TransactionInputUnresolved transaction,
        Dictionary<string, object> providedValues,
        Dictionary<string, ITransactionOperationUnresolved> operationsDictionary)
    {
        var transactionStopwatch = Stopwatch.StartNew();
        
        // generate dynamic variable for transaction.DynamicVariables
        foreach (var operationReferenceId in transaction.Operations.Select(t => t.OperationReferenceId))
        {
            var operationStopwatch = Stopwatch.StartNew();
            
            if (!operationsDictionary.TryGetValue(operationReferenceId, out var operation))
            {
                _logger.LogWarning("Could not find operation with ID {OperationReferenceId}", operationReferenceId);
                return;
            }

            var operationCorrelationId = Guid.NewGuid(); /* TODO: correlation IDs should be hierarchical and passed down */
            using var _ = _logger.BeginScope(new Dictionary<string, object>
            {
                { "OperationReferenceId", operationReferenceId },
                { "OperationCorrelationId",  operationCorrelationId },
            });

            // TODO: add logging of operation type and maybe some extra details
            
            var didResolve = _transactionOperationService.TryResolve(operation, providedValues, out var resolved);
            if (!didResolve)
            {
                _logger.LogWarning("Failed to resolve operation");
                return;
            }

            var didConvert = _transactionOperationService.TryConvertToExecutable(resolved, out var transactionOperationBaseExecutable);
            if (!didConvert)
            {
                _logger.LogWarning("Failed to convert operation into executable");
                return;
            }
            
            var result = await ExecuteOperation(transactionOperationBaseExecutable, operationCorrelationId);

            if (result is null)
            {
                // sleep operation
                continue;
            }

            var returnValues = await ExtractReturnValues(operation, result);

            // Todo: this simply adds new return values to all provided Values,
            // if we really only want to pass what the next operation uses it gets more tricky
            foreach (var p in returnValues)
            {
                // TODO: we probably want to just override providedValues
                // for example when reusing same operation in a transaction
                // if (!providedValues.ContainsKey(p.Key))
                // {
                //     providedValues.Add(p.Key, p.Value);
                // }
                providedValues[p.Key] = p.Value;
            }

            _logger.LogInformation("Operation finished in {ElapsedMs} milliseconds", operationStopwatch.ElapsedMilliseconds);
        }
        
        _logger.LogInformation("Transaction finished in {ElapsedMs} milliseconds", transactionStopwatch.ElapsedMilliseconds);
    }

    private async Task<Dictionary<string, object>> ExtractReturnValues(
        HttpOperationInputUnresolved operation,
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

    private async Task<Dictionary<string, object>> ExtractReturnValues(
        ITransactionOperationUnresolved operation,
        object result)
    {

        try
        {
            var op = (HttpOperationInputUnresolved)operation;
            if (op.Response is not null)
            {
                return result switch
                {
                    HttpResponseMessage responseMessage => await ExtractReturnValuesFromHttpMessage(op.Response,
                        responseMessage),
                    _ => throw new ArgumentOutOfRangeException(nameof(result), result, null)
                };
            }
        }
        catch
        {
            // ignored
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

    private async Task<object> ExecuteOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable,
        Guid operationCorrelationId)
    {
        return transactionOperationbaseExecutable.Type switch
        {
            OperationType.Http => await ExecuteHttpRequestOperation(transactionOperationbaseExecutable, operationCorrelationId),
            OperationType.Sleep => await ExecuteSleepOperation(transactionOperationbaseExecutable),
            _ => throw new ArgumentOutOfRangeException()
        };
    }

    private async Task<HttpResponseMessage> ExecuteHttpRequestOperation(
        TransactionOperationExecutableBase transactionOperationbaseExecutable, 
        Guid operationCorrelationId)
    {
        var httpClient = _httpClientFactory.CreateClient();
        var requestMessage = new HttpRequestMessage();

        // TODO: is using explicit cast really best solution here?
        var executable = (HttpOperationTransactionExecutable)transactionOperationbaseExecutable;
        executable.PrepareRequestMessage(requestMessage);
        requestMessage.Headers.Add("X-Correlation-Id", operationCorrelationId.ToString());
        return await httpClient.SendAsync(requestMessage);
    }

    private async Task<object> ExecuteSleepOperation(
        TransactionOperationExecutableBase transactionOperationBaseExecutable)
    {
        var executable = (SleepOperationTransactionExecutable)transactionOperationBaseExecutable;
        await executable.Sleep();
        return null;
    }
}
