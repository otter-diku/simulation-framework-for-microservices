using System.Collections;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.Extensions.Logging.Abstractions;


namespace MicroservicesSimulationFramework.Core.Tests.Integration;

/// <summary>
/// Start eShopOnContainers before running these tests. 
/// </summary>
public class EShopIntegrationTests
{
    
    [TestCaseSource(typeof(CatalogInputCases))]
    public async Task TestCatalogOperations(string fileName, string input)
    {
        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        Assert.True(result);
        Assert.NotNull(parsedInput);

        var providedArgs = new Dictionary<string, object>()
        {
            { "arg1", 42 },
        };

        var transactionOperation = sut.Convert(parsedInput, providedArgs);
        
        var httpClient = new HttpClient();
        var req = new HttpRequestMessage();
        
        transactionOperation.PrepareRequestMessage(req);

        var response = await httpClient.SendAsync(req);
        
        Assert.True(response.IsSuccessStatusCode);
    }
    
    private class CatalogInputCases : IEnumerable
    {

        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Integration/Catalog")
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