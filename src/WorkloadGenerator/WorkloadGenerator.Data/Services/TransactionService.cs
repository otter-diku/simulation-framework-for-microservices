using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public class TransactionService : ITransactionService
{
    private readonly ILogger<TransactionService> _logger;
    
    public TransactionService(ILogger<TransactionService> logger)
    {
        _logger = logger;
    }
    
    private readonly JsonSerializerOptions _jsonSerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters =
        {
            new JsonStringEnumConverter(JsonNamingPolicy.CamelCase)
        }
    };
    
    public bool TryParseInput(string json, out TransactionInput transactionInput)
    {
        transactionInput = null!;
        try
        {
            transactionInput =
                JsonSerializer.Deserialize<TransactionInput>(json, _jsonSerializerOptions);

            return transactionInput is not null;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for operation");
            
            return false;
        }
        
    }
}