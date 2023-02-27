    using System.Runtime.InteropServices.JavaScript;
    using System.Text.Json;
    using System.Text.Json.Nodes;
    using System.Text.RegularExpressions;
    using System.Transactions;
    using Microsoft.Extensions.Logging;
    using WorkloadGenerator.Data.Models;
    using WorkloadGenerator.Data.Models.Operation;
    using WorkloadGenerator.Data.Models.Operation.Http;
    using WorkloadGenerator.Data.Models.Transaction;

    namespace WorkloadGenerator.Data.Services;

    public class TransactionRunnerService
    {
        private readonly ITransactionOperationService _transactionOperationService;
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<TransactionRunnerService> _logger;
        public TransactionRunnerService(ITransactionOperationService transactionOperationService, IHttpClientFactory httpClientFactory,
            ILogger<TransactionRunnerService> logger)
        {
            _transactionOperationService = transactionOperationService;
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }
        
        public async Task Run(
            TransactionInput transaction, 
            Dictionary<string, object> providedValues, 
            Dictionary<string, HttpOperationInputUnresolved> operationsDictionary)
        {
            // generate dynamic variable for transaction.DynamicVariables
            for (var i = 0; i < transaction.Operations.Count; i++)
            {
                var opRefId = transaction.Operations[i].OperationReferenceId;
                if (!operationsDictionary.TryGetValue(opRefId, out var operation))
                {
                    throw new Exception($"Could not find operation with ID {opRefId}");
                }

                _transactionOperationService.TryResolve(operation, providedValues, out var resolved);         
                _transactionOperationService.TryConvertToExecutable(resolved, out var transactionOperationbaseExecutable);

                var result = await ExecuteOperation(transactionOperationbaseExecutable);
                
                var returnValues = ExtractReturnValues(operation, result);
            }
        }

        private Dictionary<string, object> ExtractReturnValues(
            HttpOperationInputUnresolved operation, 
            object result)
        {
            if (operation.ReturnValues is not null)
            {
                return result switch
                {
                    HttpResponseMessage responseMessage => ExtractReturnValuesFromHttpMessage(operation.ReturnValues,
                        responseMessage),
                    _ => throw new ArgumentOutOfRangeException(nameof(result), result, null)
                };            
            }
            else
            {
                return new Dictionary<string, object>();
            }
        }

        private Dictionary<string, object> ExtractReturnValuesFromHttpMessage(
            ReturnValue[] operationReturnValues, 
            HttpResponseMessage responseMessage)
        {
            var extractedValues = new Dictionary<string, object>();
            foreach (var returnValue in operationReturnValues)
            {
                var value = returnValue.Value;
                
                if (value.StartsWith("response.statuscode"))
                {
                    // todo 
                }   
                else if (value.StartsWith("response.headers"))
                {
                    // todo
                }
                else if (value.StartsWith("response.payload"))
                {
                    try {                
                        var response = JsonNode.Parse(responseMessage.Content.ReadAsStream());
                        var payloadPath = value.Replace("response.payload", "");
                        
                        var finalNode = payloadPath.Split(".")
                            .SelectMany(p => p.Split("["))
                            .Select(p => p.Replace("[", "").Replace("]", ""))
                            .Aggregate(response, (current, part) => current[part]);
                        extractedValues.Add(returnValue.Key, finalNode);
                    }
                    catch (Exception exception)
                    {
                        _logger.LogWarning(exception,
                            $"Failed trying to extract resultValue. Key: {returnValue.Key}," +
                            $" value: {returnValue.Value}, Response: {responseMessage}"
                        );
                        throw;
                    }                    
                }
            }

            return extractedValues;
        }
        
        
        private async Task<object> ExecuteOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
        {
            return transactionOperationbaseExecutable.Type switch
            {
                OperationType.Http => await ExecuteHttpRequestOperation(transactionOperationbaseExecutable),
                OperationType.Sleep => await ExecuteSleepOperation(transactionOperationbaseExecutable),
                _ => throw new ArgumentOutOfRangeException()
            };
        }

        private async Task<HttpResponseMessage> ExecuteHttpRequestOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
        {
            var httpClient = _httpClientFactory.CreateClient();
            var requestMessage = new HttpRequestMessage();
            
            // TODO: is using explicit cast really best solution here?
            var executable = (HttpOperationTransactionExecutable) transactionOperationbaseExecutable;
            executable.PrepareRequestMessage(requestMessage);
            return await httpClient.SendAsync(requestMessage);
        }

        private async Task<object> ExecuteSleepOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
        {
            throw new NotImplementedException();
        }
    }