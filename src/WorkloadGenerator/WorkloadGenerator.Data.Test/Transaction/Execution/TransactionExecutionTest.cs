using System.Net.Http.Json;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Services;
using HttpMethod = WorkloadGenerator.Data.Models.Operation.Http.HttpMethod;

namespace WorkloadGenerator.Data.Test.Transaction.Execution;

public class TransactionExecutionTest
{

[Test]
    public async Task TestExtractingValues()
    {
        var payloadString =
        """
        {
          "key1": {
            "key2": 2,
            "key3": [ "look", "what", "we", "have", "done"],
            "key4": {
              "price": 42
            }
          }
        }
        """;
        var operations = new Dictionary<string, HttpOperationInputUnresolved>();
        var op = new HttpOperationInputUnresolved()
        {
            Id = "operation-1",
            HttpMethod = HttpMethod.Post,
            Type = OperationType.Http,
            RequestPayload = new HttpOperationRequestPayloadUnresolved()
            {
                Type = HttpPayloadType.Json,
                Content = JsonSerializer.Deserialize<object>(payloadString)!
            },
            ReturnValues = new ReturnValue[]
            {
                new ReturnValue { Key = "val1", Value = "response.payload", Type = ReturnValueType.Object },
                new ReturnValue { Key = "val2", Value = "response.payload.key1.key3", Type = ReturnValueType.Array },
                new ReturnValue
                    { Key = "val3", Value = "response.payload.key1.key4.price", Type = ReturnValueType.Number },
                new ReturnValue { Key = "val4", Value = "response.payload.key1.key3[2]", Type = ReturnValueType.String }
            },
            Url = "https://httpbin.org/anything"
        };
        operations.Add("operation-1", op);

        var transaction = new TransactionInput()
        {
            Id = "transaction-1",
            Operations = new List<TransactionInput.Operation>()
            {
                new TransactionInput.Operation()
                {
                    Id = "op-1",
                    OperationReferenceId = "operation-1",
                    ProvidedValues = Array.Empty<TransactionInput.Operation.ProvidedValue>()
                }
            }
        };

        var transactionOperationService =
            new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);

        var httpClientFactory = new DefaultHttpClientFactory();
        var sut = new TransactionRunnerService(
            transactionOperationService,
            httpClientFactory,
            NullLogger<TransactionRunnerService>.Instance
        );

        var providedValues = new Dictionary<string, object>();
        await sut.Run(transaction, providedValues, operations);

        Assert.AreEqual(((JsonNode)providedValues["val1"]).ToJsonString(),
            JsonNode.Parse(payloadString).ToJsonString());
        Assert.AreEqual(((JsonNode)providedValues["val2"]).ToJsonString(),
            "[\"look\",\"what\",\"we\",\"have\",\"done\"]");
        Assert.AreEqual(((JsonNode)providedValues["val3"]).ToJsonString(),
            "42");
        Assert.AreEqual(((JsonNode)providedValues["val4"]).ToJsonString(),
            "\"we\"");
    }

    private class TestClass
    {

        public Dictionary<string, object> key1 { get; set; }
    }

    /// <summary>
    /// Taken from https://stackoverflow.com/questions/52576394/create-default-httpclientfactory-for-integration-test
    /// </summary>
    public sealed class DefaultHttpClientFactory : IHttpClientFactory, IDisposable
    {
        private readonly Lazy<HttpMessageHandler> _handlerLazy = new (() => new HttpClientHandler());

        public HttpClient CreateClient(string name) => new (_handlerLazy.Value, disposeHandler: false);

        public void Dispose()
        {
            if (_handlerLazy.IsValueCreated)
            {
                _handlerLazy.Value.Dispose();
            }
        }
    }
}
