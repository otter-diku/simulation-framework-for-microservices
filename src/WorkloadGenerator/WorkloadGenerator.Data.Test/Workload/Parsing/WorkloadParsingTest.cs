using System.Collections;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Data.Test.Transaction.Parsing;

namespace WorkloadGenerator.Data.Test.Workload.Parsing;

public class WorkloadParsingTest
{
    [TestCaseSource(typeof(ValidOperationInputCases))]
    public void TestValidCases(string fileName, string input)
    {
        var sut = new WorkloadService(NullLogger<WorkloadService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.True(result);
        Assert.NotNull(parsedInput);
    }

    [TestCaseSource(typeof(InvalidOperationInputCases))]
    public void TestInvalidCases(string fileName, string input)
    {
        var sut = new WorkloadService(NullLogger<WorkloadService>.Instance);
        var result = sut.TryParseInput(input, out var parsed);
        Assert.False(result);
    }
    
    
    private class ValidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return WorkloadParsingTest.GetEnumerator("Workload/Parsing/Valid");
        }
    }
    
    private class InvalidOperationInputCases : IEnumerable
    {
        public IEnumerator GetEnumerator()
        {
            return WorkloadParsingTest.GetEnumerator("Workload/Parsing/Invalid");
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