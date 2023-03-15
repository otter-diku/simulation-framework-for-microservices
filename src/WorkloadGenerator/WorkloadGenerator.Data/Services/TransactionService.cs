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
                "Failed trying to deserialize input data for transaction");

            return false;
        }
    }

    public bool TryResolve(TransactionInputUnresolved unresolved,
        Dictionary<string, object> providedValues,
        HashSet<string> operationReferenceIds,
        out TransactionInputResolved resolved)
    {
        resolved = null!;

        if (!Utilities.ValidateArguments(unresolved.Arguments, providedValues))
        {
            return false;
        }

        if (!ValidateOperationReferenceIds(unresolved.Operations, operationReferenceIds))
        {
            return false;
        }

        var localProvidedValues = DeepCopy(providedValues);
        localProvidedValues = Utilities.AddDynamicValues(unresolved.DynamicVariables, localProvidedValues);

        resolved = new TransactionInputResolved()
        {
            Id = Guid.NewGuid(),
            TemplateId = unresolved.TemplateId,
            Operations = unresolved.Operations,
            ProvidedValues = localProvidedValues
        };

        return true;
    }

    private bool ValidateOperationReferenceIds(List<OperationReference> unresolvedOperations, HashSet<string> operationReferenceIds)
    {
        var unknownOperationReference =
            unresolvedOperations.FirstOrDefault(op => !operationReferenceIds.Contains(op.OperationReferenceId));

        if (unknownOperationReference is null)
        {
            return true;
        }

        _logger.LogWarning(
            "{MethodName} failed: unknown operation reference {OperationReferenceId}",
            nameof(ValidateOperationReferenceIds),
            unknownOperationReference.OperationReferenceId);

        return false;
    }

    private Dictionary<string, object> DeepCopy(Dictionary<string, object> providedValues)
    {
        var serialized = JsonSerializer.Serialize(providedValues, _jsonSerializerOptions);
        return JsonSerializer.Deserialize<Dictionary<string, object>>(serialized)!;
    }
}