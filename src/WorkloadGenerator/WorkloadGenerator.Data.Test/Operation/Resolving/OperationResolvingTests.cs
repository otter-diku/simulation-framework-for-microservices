using System.Collections;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Test.Operation.Resolving;

public class OperationResolvingTests
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input, string arguments, string expectedResult)
    {
        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var parsingResult = sut.TryParseInput(input, out var parsedInput);
        Assert.True(parsingResult);
        Assert.NotNull(parsedInput);
        
        var parsedArguments = JsonSerializer.Deserialize<Dictionary<string, object>>(arguments);
        var resolved = sut.Resolve<JsonNode>(parsedInput, parsedArguments);
        var expectedParsed = JsonSerializer.Deserialize<TransactionOperationInputResolved<JsonNode>>(expectedResult, _jsonSerializerOptions)!;

        Assert.AreEqual(JsonSerializer.Serialize(resolved), JsonSerializer.Serialize(expectedParsed));
        //Assert.True(resolved.Equals(expectedParsed));
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
                    File.ReadAllText(Path.Combine(Directory.GetParent(tuple.FullFileName).Parent.FullName, "Arguments", tuple.FileName)),
                    File.ReadAllText(Path.Combine(Directory.GetParent(tuple.FullFileName).Parent.FullName, "ExpectedResult", tuple.FileName))
                })
                .GetEnumerator();
        }
    }

    private static IEnumerable<string> GetFilesFromDirectory(string relativePath)
        => Directory.GetFiles(Path.Combine(Directory.GetCurrentDirectory(), relativePath));
    
    private readonly JsonSerializerOptions _jsonSerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters =
        {
            new JsonStringEnumConverter(JsonNamingPolicy.CamelCase)
        }
    };
}