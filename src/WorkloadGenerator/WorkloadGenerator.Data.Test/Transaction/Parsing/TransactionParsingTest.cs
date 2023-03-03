using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Test.Transaction.Parsing;
using System.Collections;

public class TransactionParsingTest
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input)
    {
        var sut = new TransactionService(NullLogger<TransactionService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.True(result);
        Assert.NotNull(parsedInput);
    }

    [TestCaseSource(typeof(InvalidOperationInputCases))]
    public void TestInvalidCases(string fileName, string input)
    {
        var sut = new TransactionService(NullLogger<TransactionService>.Instance);
        var result = sut.TryParseInput(input, out var parsed);
        Assert.False(result);
    }
    
    private class ValidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return TransactionParsingTest.GetEnumerator("Transaction/Parsing/Valid");
        }
    }
    
    private class InvalidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return TransactionParsingTest.GetEnumerator("Transaction/Parsing/Invalid");
        }
    }

    
    private static IEnumerator GetEnumerator(string relativePath)
    {
        return GetFilesFromDirectory(relativePath)
            .Select(file => new object[]
            {
                file.Split("/").Last(),
                File.ReadAllText(file)
            })
            .GetEnumerator();
    }

    private static IEnumerable<string> GetFilesFromDirectory(string relativePath)
        => Directory.GetFiles(Path.Combine(Directory.GetCurrentDirectory(), relativePath));
}