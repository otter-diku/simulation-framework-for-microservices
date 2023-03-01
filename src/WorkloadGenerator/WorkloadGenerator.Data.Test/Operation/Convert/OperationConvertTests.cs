using System.Text.Json.Nodes;
using Microsoft.Extensions.Logging.Abstractions;
using Newtonsoft.Json;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Services;
using HttpMethod = WorkloadGenerator.Data.Models.Operation.Http.HttpMethod;
using JsonSerializer = System.Text.Json.JsonSerializer;

namespace WorkloadGenerator.Data.Test.Operation.Convert;

public class OperationConvertTests
{

    [Test]
    public async Task ConvertTests()
    {
        var resolvedInput =
            new HttpOperationInputResolved()
            {
                Headers = new List<Header>() { new() { Key = "header1", Value = "value1" } },
                HttpMethod = HttpMethod.Post,
                Id = "some-string",
                RequestPayload = new JsonPayloadResolved() { Content = JsonNode.Parse(JsonSerializer.Serialize(new TestClass() {ItemId = 42})) },
                QueryParameters = new List<QueryParameter>() { new() { Key = "a", Value = "b" } },
                Type = OperationType.Http,
                Url = "http://example.com"
            };

        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var isConvertSuccessful = sut.TryConvertToExecutable(resolvedInput, out var executable);
        Assert.IsTrue(isConvertSuccessful);

        var httpMessage = new HttpRequestMessage();

        var httpOperationExecutable = executable as HttpOperationTransactionExecutable;
        httpOperationExecutable.PrepareRequestMessage(httpMessage);
        
        Assert.AreEqual(httpMessage.RequestUri.AbsoluteUri, "http://example.com/?a=b");
        Assert.AreEqual(httpMessage.Method.ToString().ToLower(), HttpMethod.Post.ToString().ToLower());
        Assert.AreEqual(httpMessage.RequestUri.Query, "?a=b");
        Assert.True(httpMessage.Headers.TryGetValues("header1", out var val));
        Assert.AreEqual(val.Single(), "value1");
        Assert.AreEqual(httpMessage.Content.Headers.ContentType.ToString(), "application/json; charset=utf-8");
        var content = await httpMessage.Content.ReadAsStringAsync();
        Assert.AreEqual(JsonSerializer.Deserialize<TestClass>(content).ItemId, 42);
    }

    [Test]
    public async Task ConvertWithoutPayloadTest()
    {
        var resolvedInput =
            new HttpOperationInputResolved()
            {
                Headers = new List<Header>() { new() { Key = "header1", Value = "value1" } },
                HttpMethod = HttpMethod.Get,
                Id = "some-string",
                QueryParameters = new List<QueryParameter>() { new() { Key = "a", Value = "b" } },
                Type = OperationType.Http,
                Url = "http://example.com"
            };

        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var isConvertSuccessful = sut.TryConvertToExecutable(resolvedInput, out var executable);
        Assert.IsTrue(isConvertSuccessful);

        var httpMessage = new HttpRequestMessage();

        var httpOperationExecutable = executable as HttpOperationTransactionExecutable;
        httpOperationExecutable.PrepareRequestMessage(httpMessage);
        
        Assert.AreEqual(httpMessage.RequestUri.AbsoluteUri, "http://example.com/?a=b");
        Assert.AreEqual(httpMessage.Method.ToString().ToLower(), HttpMethod.Get.ToString().ToLower());
        Assert.AreEqual(httpMessage.RequestUri.Query, "?a=b");
        Assert.True(httpMessage.Headers.TryGetValues("header1", out var val));
        Assert.AreEqual(val.Single(), "value1");
        Assert.IsNull(httpMessage.Content);
    }
    
    private class TestClass
    {
        public int ItemId { get; set; }
    } 
}