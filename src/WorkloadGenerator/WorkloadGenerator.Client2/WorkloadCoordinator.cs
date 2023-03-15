using WorkloadGenerator.Client;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Client2;

/// <summary>
/// This class is responsible for executing a given Workload / scenario
/// by creating the worker grains which execute the requests against
/// the application that is being simulated.
/// </summary>
public class WorkloadCoordinator
{
    private WorkloadScheduler _workloadScheduler;

    public WorkloadCoordinator(WorkloadScheduler workloadScheduler)
    {
        _workloadScheduler = workloadScheduler;
    }

    // public async Task RunWorkload(
    //     WorkloadInputUnresolved workloadToRun,
    //     Dictionary<string, TransactionInputUnresolved> transactions,
    //     Dictionary<string, ITransactionOperationUnresolved> operations)
    // {
    //     // need to complete transactions that are specified (counts)
    //     // need algorithm to select next transaction + number of concurrent transactions
    //     // running
    //
    //     // spawn worker threads according to numbers with timers?
    //     // then start them
    //     var txCounts = workloadToRun.Transactions.Select(t => t.Count);
    //     var txToExecute = new List<string>();
    //     foreach (var txRef in workloadToRun.Transactions)
    //     {
    //         txToExecute.AddRange(Enumerable.Repeat<string>(txRef.Id, txRef.Count));
    //     }
    //     // TODO: shuffle list for now use guid but probably not optimal
    //     var txStack = new Stack<string>(txToExecute.OrderBy(a => Guid.NewGuid()));
    //
    //     var maxRate = 10;
    //     if (workloadToRun.MaxConcurrentTransactions is not null)
    //     {
    //         maxRate = (int)workloadToRun.MaxConcurrentTransactions;
    //     }
    //
    //     var tasks = new List<Task>();
    //     // Here we simply spawn a worker grain for each transaction and wait for their completion
    //     while (txStack.Count != 0)
    //     {
    //         var nextId = txStack.Pop();
    //         var txRef =
    //             workloadToRun.Transactions.Find(tr => tr.Id == nextId);
    //
    //         
    //         var tx = transactions[txRef.TransactionReferenceId];
    //         var txOpsRefs = tx.Operations.Select(o => o.OperationReferenceId).ToHashSet();
    //         var txOps =
    //             operations.Where(o => txOpsRefs.Contains(o.Key))
    //                 .ToDictionary(x => x.Key, x => x.Value);
    //         Console.WriteLine($"Starting tx: {txRef.Id}");
    //         var task = worker.ExecuteTransaction(workloadToRun, txRef,
    //             transactions[txRef.TransactionReferenceId], txOps, _httpClientFactory);
    //         tasks.Add(task);
    //     }
    //
    //     await Task.WhenAll(tasks);
    // }

    public async Task ScheduleWorkload(
            WorkloadInputUnresolved workloadToRun,
            Dictionary<string, TransactionInputUnresolved> transactions,
            Dictionary<string, ITransactionOperationUnresolved> operations)
    {
        var txStack = GetTransactionsToExecute(workloadToRun);
        // var maxRate = GetMaxRate(workloadToRun);

        // init Scheduler here, which will spawn workerGrains and create queue

        while (txStack.Count != 0)
        {
            // var txOpsRefs = tx.Operations.Select(o => o.OperationReferenceId).ToHashSet();
            // var txOps =
            //     operations.Where(o => txOpsRefs.Contains(o.Key))
            //         .ToDictionary(x => x.Key, x => x.Value);
            
            // Generate providedValues with Generators
            var executableTx = CreateExecutableTransaction(workloadToRun, txStack.Pop(), transactions, operations);

            // Submit transaction to scheduler
            Console.WriteLine($"Submit tx: {executableTx.Transaction.TemplateId} to scheduler");

            await _workloadScheduler.SubmitTransaction(executableTx);
        }

        Console.ReadKey();
        await _workloadScheduler.WaitForEmptyQueue();
    }

    private static ExecutableTransaction CreateExecutableTransaction(WorkloadInputUnresolved workloadToRun,
        string id,
        Dictionary<string, TransactionInputUnresolved> transactionsByReferenceId,
        Dictionary<string, ITransactionOperationUnresolved> operationsByReferenceId)
    {
        var providedValues = new Dictionary<string, object>();
        
        var txRef =
            workloadToRun.Transactions.Find(t => t.Id == id);
        
        foreach (var genRef in txRef.Data)
        {
            var generatorInput = workloadToRun.Generators.First(g => g.Id == genRef.GeneratorReferenceId);
            var generator = GeneratorFactory.GetGenerator(generatorInput);
            providedValues.Add(genRef.Name, generator.Next());
        }

        var executableTx = new ExecutableTransaction()
        {
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
        var txToExecute = new List<string>();
        foreach (var txRef in workloadToRun.Transactions)
        {
            txToExecute.AddRange(Enumerable.Repeat(txRef.Id, txRef.Count));
        }

        // TODO: shuffle list for now use guid but probably not optimal
        var txStack = new Stack<string>(txToExecute.OrderBy(a => Guid.NewGuid()));
        return txStack;
    }
}
