using System.Text.Json;
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
        var operations = new Dictionary<string, ITransactionOperationUnresolved>();
        var op = new HttpOperationInputUnresolved()
        {
            TemplateId = "operation-1",
            HttpMethod = HttpMethod.Post,
            Type = OperationType.Http,
            RequestPayload = new HttpOperationRequestPayloadUnresolved()
            {
                Type = HttpPayloadType.Json,
                Content = JsonSerializer.Deserialize<object>(payloadString)!
            },
            Response = new HttpOperationResponseInput()
            {
                Payload = new HttpOperationResponsePayloadInput()
                {
                    ReturnValues = new[]
                    {
                        new ReturnValue { Key = "val1", Value = "$.json", Type = ReturnValueType.Object },
                        new ReturnValue { Key = "val2", Value = "$.json.key1.key3", Type = ReturnValueType.Array },
                        new ReturnValue
                            { Key = "val3", Value = "$.json.key1.key4.price", Type = ReturnValueType.Number },
                        new ReturnValue { Key = "val4", Value = "$.json.key1.key3[2]", Type = ReturnValueType.String }
                    },
                    Type = HttpPayloadType.Json
                }
            },
            Url = "https://httpbin.org/anything"
        };
        operations.Add("operation-1", op);

        var transaction = new TransactionInputUnresolved()
        {
            TemplateId = "transaction-1",
            Operations = new List<OperationReference>()
            {
                new()
                {
                    Id = "op-1",
                    OperationReferenceId = "operation-1"
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

        Assert.AreEqual(JsonSerializer.Serialize((JsonElement)providedValues["val1"]),
            JsonSerializer.Serialize(JsonDocument.Parse(payloadString).RootElement));
        Assert.AreEqual(JsonSerializer.Serialize((JsonElement)providedValues["val2"]),
            "[\"look\",\"what\",\"we\",\"have\",\"done\"]");
        Assert.AreEqual(JsonSerializer.Serialize((JsonElement)providedValues["val3"]),
            "42");
        Assert.AreEqual(JsonSerializer.Serialize((JsonElement)providedValues["val4"]),
            "\"we\"");
    }


    /// <summary>
    /// Taken from https://stackoverflow.com/questions/52576394/create-default-httpclientfactory-for-integration-test
    /// </summary>
    public sealed class DefaultHttpClientFactory : IHttpClientFactory, IDisposable
    {
        private readonly Lazy<HttpMessageHandler> _handlerLazy = new(() => new HttpClientHandler());

        public HttpClient CreateClient(string name) => new(_handlerLazy.Value, disposeHandler: false);

        public void Dispose()
        {
            if (_handlerLazy.IsValueCreated)
            {
                _handlerLazy.Value.Dispose();
            }
        }
    }
}