using System.Collections;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Data.Test.Transaction.Parsing;

public class TransactionParsingTest
{
    
    [TestCaseSource(typeof(ValidTransactionInputCases))]
    public void  TestValidCases(string fileName, string input)
    {
        var sut = new TransactionService(NullLogger<TransactionService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.True(result);
        Assert.NotNull(parsedInput);
    }
    
     
    [TestCaseSource(typeof(InvalidTransactionInputCases))]
    public void  TestInvalidCases(string fileName, string input)
    {
        var sut = new TransactionService(NullLogger<TransactionService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.False(result);
        Assert.Null(parsedInput);
    }
    
    private class InvalidTransactionInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Transaction/Parsing/Invalid")
                .Select(file => new object[]
                {
                    file.Split("/").Last(), 
                    File.ReadAllText(file)
                })
                .GetEnumerator();
        }
    }
    
    private class ValidTransactionInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Transaction/Parsing/Valid")
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

