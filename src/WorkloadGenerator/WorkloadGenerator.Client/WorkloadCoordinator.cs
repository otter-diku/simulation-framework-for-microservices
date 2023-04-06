using Microsoft.Extensions.Logging;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Client;

/// <summary>
/// This class is responsible for executing a given Workload / scenario
/// by creating the worker grains which execute the requests against
/// the application that is being simulated.
/// </summary>
public class WorkloadCoordinator : IWorkloadCoordinator
{
    private readonly ILogger<WorkloadCoordinator> _logger;
    private readonly IWorkloadScheduler _workloadScheduler;

    public WorkloadCoordinator(ILogger<WorkloadCoordinator> logger, IWorkloadScheduler workloadScheduler)
    {
        _logger = logger;
        _workloadScheduler = workloadScheduler;
    }

    public async Task ScheduleWorkload(
            WorkloadInputUnresolved workloadToRun,
            Dictionary<string, TransactionInputUnresolved> transactions,
            Dictionary<string, IOperationUnresolved> operations)
    {
        var workloadCorrelationId = Guid.NewGuid(); /* TODO: correlation IDs should be hierarchical and passed down */
        using var _ = _logger.BeginScope(new Dictionary<string, object>()
        {
            { "WorkloadTemplateId", workloadToRun.TemplateId },
            { "WorkloadCorrelationId", workloadCorrelationId }
        });

        _workloadScheduler.Init(GetMaxRate(workloadToRun));

        var txStack = GetTransactionsToExecute(workloadToRun);

        _logger.LogInformation("{TransactionCount} transactions to execute", txStack.Count);
        
        var generators = new Dictionary<String, IGenerator>();
        foreach (var generator in workloadToRun.Generators)
        {
            generators.Add(generator.Id, GeneratorFactory.GetGenerator(generator));
        }        
        while (txStack.Count != 0)
        {

            // Generate providedValues with Generators
            var executableTx = CreateExecutableTransaction(
                workloadCorrelationId, workloadToRun, generators,
                txStack.Pop(), transactions, operations);

            await _workloadScheduler.SubmitTransaction(executableTx);
        }

        // TODO: we need to find a way of getting some information back from the grains to know that they are finished
        _logger.LogInformation("All transactions have been submitted to the scheduler");
    }

    private static ExecutableTransaction CreateExecutableTransaction(
        Guid workloadCorrelationId,
        WorkloadInputUnresolved workloadToRun,
        Dictionary<string, IGenerator> generators,
        string id,
        Dictionary<string, TransactionInputUnresolved> transactionsByReferenceId,
        Dictionary<string, IOperationUnresolved> operationsByReferenceId)
    {
        var providedValues = new Dictionary<string, object>();

        var txRef =
            workloadToRun.Transactions.Find(t => t.Id == id);

        foreach (var genRef in txRef!.Data)
        {
            providedValues.Add(genRef.Name, generators[genRef.GeneratorReferenceId].Next());
        }

        var executableTx = new ExecutableTransaction()
        {
            WorkloadCorrelationId = workloadCorrelationId,
            Transaction = transactionsByReferenceId[txRef.TransactionReferenceId],
            Operations = operationsByReferenceId,
            ProvidedValues = providedValues
        };
        return executableTx;
    }


    private static int GetMaxRate(WorkloadInputUnresolved workloadToRun)
    {
        var maxRate = 10;
        if (workloadToRun.MaxConcurrentTransactions is not null)
        {
            maxRate = (int)workloadToRun.MaxConcurrentTransactions;
        }

        return maxRate;
    }

    private static Stack<string> GetTransactionsToExecute(WorkloadInputUnresolved workloadToRun)
    {
        var transactionsToExecute = workloadToRun.Transactions
            .SelectMany(txRef => Enumerable.Repeat(txRef.Id, txRef.Count))
            .ToList();

        // TODO: shuffle list for now use guid but probably not optimal
        return new Stack<string>(transactionsToExecute.OrderBy(a => Guid.NewGuid()));
    }
}
