using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Sleep;

namespace WorkloadGenerator.Data.Services;

public class SleepOperationExecutionService : IOperationExecutionService
{
    private readonly ILogger<SleepOperationExecutionService> _logger;
    private readonly IOperationService _operationService;

    public SleepOperationExecutionService(ILogger<SleepOperationExecutionService> logger, 
        IOperationService operationService)
    {
        _logger = logger;
        _operationService = operationService;
    }
    
    public bool CanHandle(IOperationUnresolved operation) => operation.Type == OperationType.Sleep;

    public async Task<Dictionary<string, object>> Execute(IOperationUnresolved unresolved, Dictionary<string, object> providedValues)
    {
        if (unresolved is not SleepOperationInputUnresolved sleepOperationInputUnresolved)
        {
            _logger.LogWarning("Invalid execution service picked");
            throw new ArgumentOutOfRangeException();
        }
        
        var didResolve = _operationService.TryResolve(sleepOperationInputUnresolved, providedValues, out var resolved);
        if (!didResolve)
        {
            _logger.LogWarning("Failed to resolve operation");
            return providedValues;
        }
        
        if (resolved is not SleepOperationInputResolved sleepOperation)
        {
            _logger.LogWarning("Invalid type of resolved operation");
            return providedValues;
        }
        
        if (!_operationService.TryConvertToExecutable(sleepOperation, out var executable))
        {
            _logger.LogWarning("Failed to convert operation to executable");
            return providedValues;
        }

        if (executable is not SleepOperationExecutable sleepOperationExecutable) 
        {
            _logger.LogWarning("Invalid executable type");
            return providedValues;
        }
        
        // TODO: Add duration/units to sleepOperationExecutable in order to log the exact information here
        _logger.LogInformation("About to take a nap. One \u1F411, two \u1F411 \u1F411, three \u1F411 \u1F411 \u1F411 ....");
        
        await sleepOperationExecutable.Sleep();
        
        _logger.LogInformation("Nap finished");

        return providedValues;
    }
}