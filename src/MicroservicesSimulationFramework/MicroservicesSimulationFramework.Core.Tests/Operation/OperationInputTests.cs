using System.Collections;
using System.Text.Json;
using System.Text.RegularExpressions;
using MicroservicesSimulationFramework.Core.Models.Input;
using MicroservicesSimulationFramework.Core.Services;
using Microsoft.Extensions.Logging.Abstractions;

namespace MicroservicesSimulationFramework.Core.Tests.Operation;

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
        var result = sut.TryParseInput(input, out _);
        Assert.False(result);
    }
    
    [Test]
    public async Task FullFlowTest()
    {
        var input = 
        """
        {
          "id": "operation-id",
          "type": "http",
          "httpMethod": "post",
          "headers": [
            {
             "key": "user-id",
             "value": "000001" 
            }
          ],
          "queryParameters": [
            {
             "key": "queryparam1",
             "value": "abcdef" 
            }
          ],
          "arguments": [
            {
                "name": "arg1",
                "required": true,
                "type": "number"
            },
            {
                "name": "arg2",
                "required": true,
                "type": "string"
            },
            {
                "name": "arg3",
                "required": true,
                "type": "null"
            },
            {
                "name": "arg4",
                "required": true,
                "type": "array"
            },
            {
                "name": "this-will-get-replaced-with-random-string",
                "required": false,
                "type": "string"
            }
          ],
          "payload": {
            "type": "json",
            "content": {
              "key1": {
                "key2": "{{arg1}}",
                "key3": [ "{{this-will-get-replaced-with-random-string}}", "{{arg2}}" ],
                "key4": "{{arg3}}",
                "key5": "{{arg4}}",
                "key6": "static-value"
              }
            }
          },
          "url": "https://httpbin.org/anything/{{arg2}}/@@key1.key6@@"
        }
        """;
        
        var sut = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var result = sut.TryParseInput(input, out var parsedInput);
        
        Assert.True(result);
        
        // These arguments would flow from the top, e.g. specified from the transaction or from the scenario
        var providedArgs = new Dictionary<string, object>()
        {
            { "arg1", 1 },
            { "arg2", "2" },
            { "arg3", null },
            { "arg4", new[] {5, 6, 7} },
            // notice no 'this-will-get-replaced-with-random-string' here - we will see this and randomly generate a string instead 
        };

        var transactionOperation = sut.Convert(parsedInput, providedArgs);

        var httpClient = new HttpClient();
        var req = new HttpRequestMessage();
        
        transactionOperation.PrepareRequestMessage(req);

        var response = await httpClient.SendAsync(req);
        
        Assert.True(response.IsSuccessStatusCode);
        
        var content = await response.Content.ReadAsStringAsync();
        // JSON below is an actual response from httpbin 
        var _ = 
        """ 
        {
          "args": {
            "queryparam1": "abcdef"
          }, 
          "data": "{\n  \"key1\": {\n    \"key2\": 1,\n    \"key3\": [\n      \"random-string\",\n      \"2\"\n    ],\n    \"key4\": null,\n    \"key5\": [\n      5,\n      6,\n      7\n    ],\n    \"key6\": \"static-value\"\n  }\n}", 
          "files": {}, 
          "form": {}, 
          "headers": {
            "Content-Length": "180", 
            "Content-Type": "application/json; charset=utf-8", 
            "Host": "httpbin.org", 
            "User-Id": "000001", 
            "X-Amzn-Trace-Id": "Root=1-63f6bf54-6dac8d623691976937e35fcf"
          }, 
          "json": {
            "key1": {
              "key2": 1, 
              "key3": [
                "random-string", 
                "2"
              ], 
              "key4": null, 
              "key5": [
                5, 
                6, 
                7
              ], 
              "key6": "static-value"
            }
          }, 
          "method": "POST", 
          "origin": "94.147.68.44", 
          "url": "https://httpbin.org/anything/2/static-value?queryparam1=abcdef"
        }
        """;
    }

    private class InvalidOperationInputCases : IEnumerable
    {

        public IEnumerator GetEnumerator()
        {
            return GetFilesFromDirectory("Operation/Invalid")
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
            return GetFilesFromDirectory("Operation/Valid")
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