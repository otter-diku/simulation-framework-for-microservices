// using System.Text.Json.Nodes;
// using System.Transactions;
// using WorkloadGenerator.Data.Models;
// using WorkloadGenerator.Data.Models.Operation;
// using WorkloadGenerator.Data.Models.Operation.Http;
// using WorkloadGenerator.Data.Models.Transaction;
//
// namespace WorkloadGenerator.Data.Services;
//
// public class TransactionRunnerService
// {
//     private readonly ITransactionOperationService _transactionOperationService;
//     private readonly IHttpClientFactory _httpClientFactory;
//
//     public TransactionRunnerService(ITransactionOperationService transactionOperationService, IHttpClientFactory httpClientFactory)
//     {
//         _transactionOperationService = transactionOperationService;
//         _httpClientFactory = httpClientFactory;
//     }
//     
//     public async Task Run(
//         TransactionInput transaction, 
//         Dictionary<string, object> providedValues, 
//         Dictionary<string, HttpOperationInputUnresolved> operationsDictionary)
//     {
//         // generate dynamic variable for transaction.DynamicVariables
//         for (var i = 0; i < transaction.Operations.Count; i++)
//         {
//             var opRefId = transaction.Operations[i].OperationReferenceId;
//             if (!operationsDictionary.TryGetValue(opRefId, out var operation))
//             {
//                 throw new Exception($"Could not find operation with ID {opRefId}");
//             }
//
//             TransactionOperationExecutableBase transactionOperationbaseExecutable = null;
//             if (operation.RequestPayload?.Type == HttpPayloadType.Json)
//             {
//                 var resolved = _transactionOperationService.Resolve<JsonNode>(operation, providedValues);
//                 transactionOperationbaseExecutable = _transactionOperationService.ConvertToExecutable(resolved);
//                 
//             }
//             else
//             {
//                 var resolved = _transactionOperationService.Resolve(operation, providedValues);
//                 transactionOperationbaseExecutable = _transactionOperationService.ConvertToExecutable(resolved);
//             }
//
//             var result = await ExecuteOperation(transactionOperationbaseExecutable);
//
//             
//             var returnValues = ExtractReturnValues(operation, result);
//         }
//     }
//
//     private Dictionary<string, object> ExtractReturnValues(
//         object result)
//     {
//         return result switch
//         {
//             HttpResponseMessage responseMessage => ExtractReturnValuesFromHttpMessage(operationReturnValues,
//                 responseMessage),
//             _ => throw new ArgumentOutOfRangeException(nameof(result), result, null)
//         };
//     }
//
//     private Dictionary<string, object> ExtractReturnValuesFromHttpMessage(
//         
//         ReturnValue[] operationReturnValues, 
//         HttpResponseMessage responseMessage)
//     {
//         foreach (var returnValue in operationReturnValues)
//         {
//              
//         }
//     }
//
//     private async Task<object> ExecuteOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
//     {
//         return transactionOperationbaseExecutable.Type switch
//         {
//             OperationType.Http => await ExecuteHttpRequestOperation(transactionOperationbaseExecutable),
//             OperationType.Sleep => await ExecuteSleepOperation(transactionOperationbaseExecutable),
//             _ => throw new ArgumentOutOfRangeException()
//         };
//     }
//
//     private async Task<HttpResponseMessage> ExecuteHttpRequestOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
//     {
//         var httpClient = _httpClientFactory.CreateClient();
//         var requestMessage = new HttpRequestMessage();
//         transactionOperationbaseExecutable.PrepareRequestMessage(requestMessage);
//         return await httpClient.SendAsync(requestMessage);
//     }
//
//     private async Task<object> ExecuteSleepOperation(TransactionOperationExecutableBase transactionOperationbaseExecutable)
//     {
//         throw new NotImplementedException();
//     }
// }