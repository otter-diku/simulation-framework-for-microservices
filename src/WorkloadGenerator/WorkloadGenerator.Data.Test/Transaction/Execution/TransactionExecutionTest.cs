using System.Net.Http.Json;
using System.Runtime.InteropServices;
using System.Text.Json;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Test.Transaction.Execution;

public class TransactionExecutionTest
{
    [Test]
    public async Task TestExtractingValues()
    {
        // send request to httpbin
        // get json payload and retrieve values
        
        var input = 
        """
        {
            "id": "operation-id",
            "type": "http",
            "httpMethod": "put",
            "requestPayload": {
                "type": "json",
                "content": {
                    "key1": {
                        "key2": 2,
                        "key3": [ "look", "what", "we", "have", "done"],
                        "key4": {
                            "price": 42 
                        },
                    }
                }
            },
            "returnValues": [
              {
                "key": "val1",
                "value": "response.payload.key1.key3[1]",
                "type": "object"
              },
              {
                "key": "val2",
                "value": "response.payload.key1.key3",
                "type": "array"
              },
              {
                "key": "val3",
                "value": "response.payload",
                "type": "object"
              },
              {
                "key": "val4",
                "value": "response.payload.key1.key4.price",
                "type": "number"
              },
            ],
            "url": "https://httpbin.org/anything"
        }
        """;
        var transactionOperationService =
            new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);

        transactionOperationService.TryParseInput(input, out var parsedInput);
        
        IServiceCollection services = new ServiceCollection();

        // var httpClientFactory =  services
        //     .BuildServiceProvider()
        //     .GetRequiredService<IHttpClientFactory>();        
        // var sut = new TransactionRunnerService(
        //     transactionOperationService,
        //     httpClientFactory,
        //     NullLogger<TransactionRunnerService>.Instance
        // );
        //
        
    }
}