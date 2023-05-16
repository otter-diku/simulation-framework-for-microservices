using System.Collections;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Services;
using HttpMethod = System.Net.Http.HttpMethod;

namespace WorkloadGenerator.Data.Test.Transaction.Resolving;

public class TransactionResolvingTest
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input, string arguments)
    {
        var sut = new TransactionService(NullLogger<TransactionService>.Instance);
        var parsingResult = sut.TryParseInput(input, out var parsedInput);
        Assert.True(parsingResult);
        Assert.NotNull(parsedInput);

        var parsedArguments = string.IsNullOrWhiteSpace(arguments)
            ? default
            : JsonSerializer.Deserialize<Dictionary<string, object>>(arguments);

        var operationReferenceIds =
            parsedInput.Operations.Select(o => o.OperationReferenceId).ToHashSet();
        var isResolvedSuccessful = sut.TryResolve(parsedInput, parsedArguments, operationReferenceIds, out var resolved);

        Assert.IsTrue(isResolvedSuccessful);
        Assert.IsInstanceOf<TransactionInputResolved>(resolved);

        if (parsedInput.Arguments is not null)
        {
            Assert.True(parsedInput.Arguments.All(a => resolved.ProvidedValues.ContainsKey(a.Name)));
        }

        if (parsedInput.DynamicVariables is not null)
        {
            Assert.True(parsedInput.DynamicVariables.All(dv => resolved.ProvidedValues.ContainsKey(dv.Name)));
        }
    }

    private class ValidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Transaction/Resolving/Valid/Input")
                .Select(fullFileName => (FullFileName: fullFileName, FileName: fullFileName.Split("/").Last()))
                .Select(tuple => new object[]
                {
                    tuple.FileName,
                    File.ReadAllText(tuple.FullFileName),
                    File.ReadAllText(Path.Combine(Directory.GetParent(tuple.FullFileName).Parent.FullName, "Arguments",
                        tuple.FileName))
                })
                .GetEnumerator();
        }
    }

    private static IEnumerable<string> GetFilesFromDirectory(string relativePath)
        => Directory.GetFiles(Path.Combine(Directory.GetCurrentDirectory(), relativePath));
}