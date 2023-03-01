using System.Collections;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Test.Operation.Parsing;

public class OperationInputTests
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input)
    {
        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.True(result);
        Assert.NotNull(parsedInput);
    }

    [TestCaseSource(typeof(InvalidOperationInputCases))]
    public void TestInvalidCases(string fileName, string input)
    {
        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var result = sut.TryParseInput(input, out var parsed);
        Assert.False(result);
    }


    private class InvalidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Operation/Parsing/Invalid")
                .Select(file => new object[]
                {
                    file.Split("/").Last(),
                    File.ReadAllText(file)
                })
                .GetEnumerator();
        }
    }

    private class ValidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Operation/Parsing/Valid")
                .Select(file => new object[]
                {
                    file.Split("/").Last(),
                    File.ReadAllText(file)
                })
                .GetEnumerator();
        }
    }

    private static IEnumerable<string> GetFilesFromDirectory(string relativePath)
        => Directory.GetFiles(Path.Combine(Directory.GetCurrentDirectory(), relativePath));
}