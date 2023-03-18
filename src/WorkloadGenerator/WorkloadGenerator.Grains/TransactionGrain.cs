using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Operation.Http;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Grains;

public class TransactionGrain : Grain, ITransactionGrain
{
    private readonly ILogger<TransactionGrain> _logger;
    private readonly ITransactionOperationService _transactionOperationService;
    private readonly ITransactionService _transactionService;
    private readonly IEnumerable<IOperationExecutor> _operationExecutors;
    private TransactionInputUnresolved? _unresolved;

    public TransactionGrain(
        ILogger<TransactionGrain> logger,
        ITransactionOperationService transactionOperationService,
        ITransactionService transactionService, 
        IEnumerable<IOperationExecutor> operationExecutors)
    {
        _logger = logger;
        _transactionOperationService = transactionOperationService;
        _transactionService = transactionService;
        _operationExecutors = operationExecutors;
    }
    
    public Task Init(TransactionInputUnresolved unresolved)
    {
        _unresolved = unresolved;
        return Task.CompletedTask;
    }

    public async Task ExecuteTransaction(IReadOnlyDictionary<string, object> providedValues,
        IReadOnlyDictionary<string, ITransactionOperationUnresolved> operationsDictionary)
    {
        _logger.LogDebug(
            "Starting execution of transaction with template ID: {TransactionTemplateId}", _unresolved.TemplateId);
        
        if (_unresolved is null)
        {
            throw new Exception();
        }

        var isResolvedSuccessfully = _transactionService.TryResolve(_unresolved, providedValues, out var resolved);

        if (!isResolvedSuccessfully)
        {
            throw new Exception();
        }

        var transactionScopedProvidedValues = new Dictionary<string, object>(providedValues);

        foreach (var opRefId in resolved.Operations.Select(
                     operationReference => operationReference.OperationReferenceId))
        {
            if (!operationsDictionary.TryGetValue(opRefId, out var unresolvedOperation))
            {
                throw new Exception($"Could not find operation with ID {opRefId}");
            }
            
            _transactionOperationService.TryResolve(unresolvedOperation, transactionScopedProvidedValues, out var resolvedOperation);

            var executor = _operationExecutors.SingleOrDefault(executor => executor.CanHandle(resolvedOperation));
            if (executor is null)
            {
                throw new ArgumentOutOfRangeException();
            }

            var returnValues = await executor.Execute(resolvedOperation);

            foreach (var returnValue in returnValues)
            {
                if (!transactionScopedProvidedValues.TryAdd(returnValue.Key, returnValue.Value))
                {
                    _logger.LogWarning("Trying to add a duplicate key: {DuplicateKey}", returnValue.Key);
                }
            }
        }
        
        _logger.LogDebug(
            "Finished execution of the transaction with template ID: {TransactionTemplateId}", _unresolved.TemplateId);
    }
}