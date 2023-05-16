using System.Collections;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging.Abstractions;
using Utilities;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Services;
using HttpMethod = WorkloadGenerator.Data.Models.Operation.Http.HttpMethod;

namespace WorkloadGenerator.Data.Test.Operation.Resolving;

public class OperationResolvingTests
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input, string arguments, string expectedResult)
    {
        var sut = new OperationService(NullLogger<OperationService>.Instance);
        var parsingResult = sut.TryParseInput(input, out var parsedInput);
        Assert.True(parsingResult);
        Assert.NotNull(parsedInput);

        var parsedArguments = string.IsNullOrWhiteSpace(arguments)
            ? default
            : JsonSerializer.Deserialize<Dictionary<string, object>>(arguments);

        var isResolvedSuccessful = sut.TryResolve(parsedInput, parsedArguments, out var resolved);
        Assert.IsTrue(isResolvedSuccessful);
        Assert.IsInstanceOf<HttpOperationInputResolved>(resolved);

        var expectedResolved =
            JsonSerializer.Deserialize<HttpOperationInputResolved>(expectedResult,
                SerializerUtils.GetGlobalJsonSerializerOptions(
                    opt =>
                    {
                        opt.Converters.Add(new HttpOperationRequestPayloadResolvedBaseConverter());
                    }));

        Assert.That(JsonSerializer.Serialize(expectedResolved), Is.EqualTo(JsonSerializer.Serialize(resolved as HttpOperationInputResolved)));
    }

    [Test]
    public void TestDynamicVariableResolving()
    {
        var unresolved = new HttpOperationInputUnresolved()
        {
            Arguments = new Argument[] { new() { Name = "arg1", Type = ArgumentType.Number } },
            DynamicVariables =
                new DynamicVariable[] { new() { Name = "var1", Type = DynamicVariableType.UnsignedInt } },
            Type = OperationType.Http,
            HttpMethod = HttpMethod.Post,
            Url = "http://example.com",
            RequestPayload = new HttpOperationRequestPayloadUnresolved()
            {
                Type = HttpPayloadType.Json,
                Content = JsonSerializer.Deserialize<object>("{\"key1\": \"{{arg1}}\", \"key2\":\"{{var1}}\"}")!
            }
        };
        var sut = new OperationService(NullLogger<OperationService>.Instance);
        var didResolveSuccessfully = sut.TryResolve(unresolved, new Dictionary<string, object>()
        {
            { "arg1", 42 }
        }, out var resolved);

        Assert.True(didResolveSuccessfully);

        var httpRequestOperationResolved = resolved as HttpOperationInputResolved;
        var json = httpRequestOperationResolved.RequestPayload as JsonPayloadResolved;
        Assert.That(json.Content["key1"].GetValue<int>(), Is.EqualTo(42));
        Assert.That(json.Content["key2"].GetValue<int>(), Is.Positive);
    }

    private class ValidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Operation/Resolving/Valid/Input")
                .Select(fullFileName => (FullFileName: fullFileName, FileName: fullFileName.Split("/").Last()))
                .Select(tuple => new object[]
                {
                    tuple.FileName,
                    File.ReadAllText(tuple.FullFileName),
                    File.ReadAllText(Path.Combine(Directory.GetParent(tuple.FullFileName).Parent.FullName, "Arguments",
                        tuple.FileName)),
                    File.ReadAllText(Path.Combine(Directory.GetParent(tuple.FullFileName).Parent.FullName,
                        "ExpectedResult", tuple.FileName))
                })
                .GetEnumerator();
        }
    }

    private static IEnumerable<string> GetFilesFromDirectory(string relativePath)
        => Directory.GetFiles(Path.Combine(Directory.GetCurrentDirectory(), relativePath));
}