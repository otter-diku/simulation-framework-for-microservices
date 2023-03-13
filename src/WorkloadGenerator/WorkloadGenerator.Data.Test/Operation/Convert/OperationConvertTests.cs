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
                TemplateId = "some-string",
                RequestPayload = new JsonPayloadResolved()
                { Content = JsonNode.Parse(JsonSerializer.Serialize(new TestClass() { ItemId = 42 })) },
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

        Assert.That(httpMessage.RequestUri.AbsoluteUri, Is.EqualTo("http://example.com/?a=b"));
        Assert.That(HttpMethod.Post.ToString().ToLower(), Is.EqualTo(httpMessage.Method.ToString().ToLower()));
        Assert.That(httpMessage.RequestUri.Query, Is.EqualTo("?a=b"));
        Assert.True(httpMessage.Headers.TryGetValues("header1", out var val));
        Assert.That(val.Single(), Is.EqualTo("value1"));
        Assert.That(httpMessage.Content.Headers.ContentType.ToString(), Is.EqualTo("application/json; charset=utf-8"));
        var content = await httpMessage.Content.ReadAsStringAsync();
        Assert.That(JsonSerializer.Deserialize<TestClass>(content).ItemId, Is.EqualTo(42));
    }

    [Test]
    public async Task ConvertWithoutPayloadTest()
    {
        var resolvedInput =
            new HttpOperationInputResolved()
            {
                Headers = new List<Header>() { new() { Key = "header1", Value = "value1" } },
                HttpMethod = HttpMethod.Get,
                TemplateId = "some-string",
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

        Assert.That(httpMessage.RequestUri.AbsoluteUri, Is.EqualTo("http://example.com/?a=b"));
        Assert.That(HttpMethod.Get.ToString().ToLower(), Is.EqualTo(httpMessage.Method.ToString().ToLower()));
        Assert.That(httpMessage.RequestUri.Query, Is.EqualTo("?a=b"));
        Assert.True(httpMessage.Headers.TryGetValues("header1", out var val));
        Assert.That(val.Single(), Is.EqualTo("value1"));
        Assert.IsNull(httpMessage.Content);
    }

    private class TestClass
    {
        public int ItemId { get; set; }
    }
}