using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public class WorkloadService : IWorkloadService
{
    private readonly ILogger<WorkloadService> _logger;
    private readonly WorkloadInputUnresolvedValidator _workloadInputUnresolvedValidator;    
    
    public WorkloadService(ILogger<WorkloadService> logger)
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
    
    public bool TryParseInput(string json, out WorkloadInputUnresolved workloadInputUnresolved)
    {
        workloadInputUnresolved = null!;
        try
        {
            workloadInputUnresolved =
                JsonSerializer.Deserialize<WorkloadInputUnresolved>(json, _jsonSerializerOptions);

            return workloadInputUnresolved is not null 
                   && _workloadInputUnresolvedValidator.Validate(workloadInputUnresolved).IsValid;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception, 
                "Failed trying to deserialize input data for transaction");

            return false;
        }
    }

    public bool TryResolve(WorkloadInputUnresolved unresolved, HashSet<string> transactionReferenceIds,
        out WorkloadInputResolved resolved)
    {
        throw new NotImplementedException();
    }
}