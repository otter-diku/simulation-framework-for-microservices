using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Orleans.Configuration;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Server;
using WorkloadGenerator.Grains;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Coordinator;

/// <summary>
/// This class is responsible for executing a given Workload / scenario
/// by creating the worker grains which execute the requests against
/// the application that is being simulated.
/// </summary>
public class WorkloadCoordinator : IDisposable
{
    private IHost _silo;
    private IClusterClient _client;
    private DefaultHttpClientFactory _httpClientFactory;

    public WorkloadCoordinator()
    { 
        
    }

    public async Task Init()
    {
        _silo = await WorkloadGeneratorServer.StartSiloAsync();
        _client = _silo.Services.GetService<IClusterClient>()!;
        _httpClientFactory = new DefaultHttpClientFactory();

    }

    public async Task RunWorkload(
        WorkloadInputUnresolved workloadToRun, 
        Dictionary<string, TransactionInputUnresolved> transactions,
        Dictionary<string, ITransactionOperationUnresolved> operations)
    {
        // need to complete transactions that are specified (counts)
        // need algorithm to select next transaction + number of concurrent transactions
        // running
        
        // spawn worker threads according to numbers with timers?
        // then start them
        var txCounts = workloadToRun.Transactions.Select(t => t.Count);
        var txToExecute = new List<string>();
        foreach (var txRef in workloadToRun.Transactions)
        {
            txToExecute.AddRange(Enumerable.Repeat<string>(txRef.TransactionReferenceId, txRef.Count));
        }
        // TODO: shuffle list for now use guid but probably not optimal
        var txStack = new Stack<string>(txToExecute.OrderBy(a => Guid.NewGuid()));

        var maxRate = 10;
        if (workloadToRun.MaxConcurrentTransactions is not null)
        {
            maxRate = (int)workloadToRun.MaxConcurrentTransactions;
        }

        var tasks = new List<Task>();
        // Here we simply spawn a worker grain for each transaction and wait for their completion
        while (txStack.Count != 0)
        {
            var nextTx = txStack.Pop();
            var worker = _client.GetGrain<IWorkerGrain>(txStack.Count,
                grainClassNamePrefix: "WorkloadGenerator.Grains.WorkerGrain");
            var tx = transactions[nextTx];
            var txOpsRefs = tx.Operations.Select(o => o.OperationReferenceId).ToHashSet();
            var txOps = 
                operations.Where(o => txOpsRefs.Contains(o.Key))
                    .ToDictionary(x => x.Key, x => x.Value);
            Console.WriteLine($"Starting tx: {nextTx}");
            var task = worker.ExecuteTransaction(workloadToRun, transactions[nextTx], txOps, _httpClientFactory);
            tasks.Add(task);
        }

        await Task.WhenAll(tasks);
    }
    
public async Task ScheduleWorkload(
        WorkloadInputUnresolved workloadToRun, 
        Dictionary<string, TransactionInputUnresolved> transactions,
        Dictionary<string, ITransactionOperationUnresolved> operations)
    {
        var txCounts = workloadToRun.Transactions.Select(t => t.Count);
        var txToExecute = new List<string>();
        foreach (var txRef in workloadToRun.Transactions)
        {
            txToExecute.AddRange(Enumerable.Repeat<string>(txRef.TransactionReferenceId, txRef.Count));
        }
        // TODO: shuffle list for now use guid but probably not optimal
        var txStack = new Stack<string>(txToExecute.OrderBy(a => Guid.NewGuid()));

        
        var maxRate = 10;
        if (workloadToRun.MaxConcurrentTransactions is not null)
        {
            maxRate = (int) workloadToRun.MaxConcurrentTransactions;
        }
        
        // init Scheduler here, which will spawn workerGrains and create queue
        var workloadScheduler = new WorkloadScheduler(maxRate, _client, _httpClientFactory);
        workloadScheduler.Init();

        while (txStack.Count != 0)
        {
            var nextTx = txStack.Pop();
            var tx = transactions[nextTx];

            var txOpsRefs = tx.Operations.Select(o => o.OperationReferenceId).ToHashSet();
            var txOps =
                operations.Where(o => txOpsRefs.Contains(o.Key))
                    .ToDictionary(x => x.Key, x => x.Value);

            // Generate providedValues with Generators
            var providedValues = new Dictionary<string, object>();
            var txRef =
                workloadToRun.Transactions.First(t => t.TransactionReferenceId == tx.TemplateId);
            foreach (var genRef in txRef.Data)
            {
                var generatorInput = workloadToRun.Generators.First(g => g.Id == genRef.GeneratorReferenceId);
                var generator = GeneratorFactory.GetGenerator(generatorInput);
                providedValues.Add(genRef.Name, generator.Next());
            }

            // Submit transaction to scheduler
            Console.WriteLine($"Submit tx: {nextTx} to scheduler");
            var executableTx = new ExecutableTransaction()
            {
                Transaction = tx,
                Operations = operations,
                ProvidedValues = providedValues
            };
            workloadScheduler.SubmitTransaction(executableTx);
        }

        await workloadScheduler.WaitForEmptyQueue();
    }    

    public void StartExecution(int numTransactions)
    {
        // var xacts = GenerateTransactionDistribution(numTransactions);
        // for (int i = 0; i < numTransactions; i++)
        // {
        //     if (xacts[i] == TransactionType.CatalogAddItem)
        //     {
        //         var worker = _client.GetGrain<IWorkerGrain>(i,
        //             grainClassNamePrefix: "WorkloadGenerator.Grains.CatalogAddItemGrain");
        //         worker.ExecuteTransaction().Wait();
        //     }
        //     else if (xacts[i] == TransactionType.CatalogUpdatePrice)
        //     {
        //         var worker = _client.GetGrain<IWorkerGrain>(i,
        //             grainClassNamePrefix: "WorkloadGenerator.Grains.CatalogUpdateItemPriceGrain");
        //         worker.ExecuteTransaction().Wait();
        //     }
        //     else if (xacts[i] == TransactionType.BasketAddItem)
        //     {
        //         var worker = _client.GetGrain<IWorkerGrain>(i,
        //             grainClassNamePrefix: "WorkloadGenerator.Grains.BasketAddItemGrain");
        //         worker.ExecuteTransaction().Wait();
        //     }
        // }
    }

    private List<TransactionType> GenerateTransactionDistribution(int numTransactions)
    {
        // want to use config to have certain distribution of xacts 
        var values = Enum.GetValues(typeof(TransactionType));
        var random = new Random();

        var xacts = new List<TransactionType>();
        for (int i = 0; i < numTransactions; i++)
        {
            var randomXact = (TransactionType)values.GetValue(random.Next(values.Length))!;
            xacts.Add(randomXact);
        }

        return xacts;
    }

    public void Dispose()
    {
        _silo.Dispose();
    }

    private sealed class DefaultHttpClientFactory : IHttpClientFactory, IDisposable
    {
        private readonly Lazy<HttpMessageHandler> _handlerLazy = new(() => new HttpClientHandler());

        public HttpClient CreateClient(string name) => new(_handlerLazy.Value, disposeHandler: false);

        public void Dispose()
        {
            if (_handlerLazy.IsValueCreated)
            {
                _handlerLazy.Value.Dispose();
            }
        }
    }    
    
}