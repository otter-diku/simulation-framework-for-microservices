using System.Diagnostics;
using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;

namespace WorkloadGenerator.Data.Services;

public class TransactionExecutionService : ITransactionExecutionService
{
    private readonly IEnumerable<IOperationExecutionService> _operationExecutionServices;
    private readonly ILogger<TransactionExecutionService> _logger;

    public TransactionExecutionService(
        IEnumerable<IOperationExecutionService> operationExecutionServices,
        ILogger<TransactionExecutionService> logger)
    {
        _operationExecutionServices = operationExecutionServices;
        _logger = logger;
    }

    public async Task Run(
        TransactionInputUnresolved transaction,
        Dictionary<string, object> providedValues,
        Dictionary<string, IOperationUnresolved> operationsDictionary)
    {
        var transactionStopwatch = Stopwatch.StartNew();

        foreach (var operationReferenceId in transaction.Operations.Select(t => t.OperationReferenceId))
        {
            using var _ = _logger.BeginScope(new Dictionary<string, object>
            {
                { "OperationReferenceId", operationReferenceId },
                { "OperationCorrelationId",  Guid.NewGuid() /* TODO: correlation IDs should be hierarchical and passed down */ },
            });

            providedValues = await ExecuteOperation(providedValues, operationsDictionary, operationReferenceId);
        }

        _logger.LogInformation("Transaction finished in {ElapsedMs} milliseconds",
            transactionStopwatch.ElapsedMilliseconds);
    }

    private async Task<Dictionary<string, object>> ExecuteOperation(Dictionary<string, object> providedValues, Dictionary<string, IOperationUnresolved> operationsDictionary,
        string operationReferenceId)
    {
        var operationStopwatch = Stopwatch.StartNew();

        if (!operationsDictionary.TryGetValue(operationReferenceId, out var operation))
        {
            _logger.LogWarning(
                "Could not find operation with ID {OperationReferenceId}, continuing with the next operation...",
                operationReferenceId);

            return providedValues;
        }

        providedValues = await ExecuteOperation(operation, providedValues);

        _logger.LogInformation("Operation finished in {ElapsedMs} milliseconds",
            operationStopwatch.ElapsedMilliseconds);

        return providedValues;
    }

    private async Task<Dictionary<string, object>> ExecuteOperation(
        IOperationUnresolved unresolved,
        Dictionary<string, object> providedValues)
    {
        var operationExecutionService
            = _operationExecutionServices.SingleOrDefault(executionService => executionService.CanHandle(unresolved));

        if (operationExecutionService is not null)
        {
            return await operationExecutionService.Execute(unresolved, providedValues);
        }

        _logger.LogWarning("Did not find an execution service for {OperationType}", unresolved.Type);
        return providedValues;
    }
}
