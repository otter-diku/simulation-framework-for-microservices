using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public class TransactionService : ITransactionService
{
    private readonly ILogger<TransactionService> _logger;
    private readonly TransactionInputUnresolvedValidator _transactionInputUnresolvedValidator;


    public TransactionService(ILogger<TransactionService> logger)
    {
        _logger = logger;
        _transactionInputUnresolvedValidator = new TransactionInputUnresolvedValidator();
    }

    private readonly JsonSerializerOptions _jsonSerializerOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        Converters =
        {
            new JsonStringEnumConverter(JsonNamingPolicy.CamelCase)
        }
    };
    
    public bool TryParseInput(string json, out TransactionInputUnresolved transactionInputUnresolved)
    {
        transactionInputUnresolved = null!;
        try
        {
            transactionInputUnresolved =
                JsonSerializer.Deserialize<TransactionInputUnresolved>(json, _jsonSerializerOptions);

            return transactionInputUnresolved is not null 
                   && _transactionInputUnresolvedValidator.Validate(transactionInputUnresolved).IsValid;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for operation");

            return false;
        }
    }
}