// See https://aka.ms/new-console-template for more information

using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging.Abstractions;
using WorkloadGenerator.Data.Services;
using Utilities;
using WorkloadGenerator.Client;
using WorkloadGenerator.Client2;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Server;

var hostBuilder = Host.CreateDefaultBuilder(args);

if (args.Length == 0)
{
    Console.WriteLine("No workload configuration folder specified");
    return;
}

var jsonFiles = new List<(string, string)>();
Helper.ReadAllJsonFiles(jsonFiles, args[0]);

if (jsonFiles is not null)
{
    var operationFiles = jsonFiles.Where(f =>
        f.Item1.StartsWith("op_")).ToList();
    var transactionFiles = jsonFiles.Where(f =>
        f.Item1.StartsWith("tx_")).ToList();
    var workloadFiles = jsonFiles.Where(f =>
        f.Item1.StartsWith("workload_")).ToList();

    // Parsing + validation
    var operationService = new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
    var operations = new Dictionary<string, ITransactionOperationUnresolved>();
    foreach (var o in operationFiles)
    {
        var parsingResult = operationService.TryParseInput(o.Item2, out var parsedOp);
        if (!parsingResult)
        {
            Console.WriteLine($"Error while parsing {o}");
            return;
        }
        operations.Add(parsedOp.TemplateId, parsedOp);
    }

    var transactionService = new TransactionService(NullLogger<TransactionService>.Instance);
    var transactions = new Dictionary<string, TransactionInputUnresolved>();
    foreach (var t in transactionFiles)
    {
        var parsingResult = transactionService.TryParseInput(t.Item2, out var parsedTx);
        if (!parsingResult)
        {
            Console.WriteLine($"Error while parsing {t}");
            return;
        }
        transactions.Add(parsedTx.TemplateId, parsedTx);
    }

    var workloadService = new WorkloadService(NullLogger<WorkloadService>.Instance);
    var workloads = new Dictionary<string, WorkloadInputUnresolved>();
    foreach (var w in workloadFiles)
    {
        var parsingResult = workloadService.TryParseInput(w.Item2, out var parsedWorkload);
        if (!parsingResult)
        {
            Console.WriteLine($"Error while parsing {w}");
            return;
        }
        workloads.Add(w.Item1, parsedWorkload);
    }

    // Create hashsets to check all referenced transactions / operations exist while resolving
    var operationReferenceIds =
        operations.Values.Select(o => o.TemplateId).ToHashSet();
    var transactionReferenceIds =
        transactions.Values.Select(t => t.TemplateId).ToHashSet();

    // TODO: do op/tx reference validation here before starting to execute workload


    // Select workload to run
    Console.WriteLine("Select workload to run:");
    var workloadNum = 1;
    foreach (var w in workloadFiles)
    {
        Console.WriteLine($"({workloadNum}) {w.Item1}");
        workloadNum++;
    }
    var input = Console.ReadLine();
    var parseResult = int.TryParse(input, out var selected);

    WorkloadInputUnresolved workloadToRun = null;
    if (parseResult && selected < workloadNum)
    {
        workloadToRun = workloads[workloadFiles[selected - 1].Item1];
    }
    
    var _ = await WorkloadGeneratorServer.StartSiloAsync();
    
    var clientHost = await WorkloadGeneratorClient2.StartClientAsync();

    
    
    var host = hostBuilder.ConfigureServices(services =>
        {
            services.AddSingleton<TransactionRunnerService>();
            services.AddHttpClient<TransactionRunnerService>();
            services.AddSingleton<ITransactionOperationService, TransactionOperationService>();
            services.AddSingleton<ITransactionService, TransactionService>();
            services.AddSingleton<IWorkloadService, WorkloadService>();
            services.AddSingleton<WorkloadScheduler>(sp =>
            {
                var clusterClient = clientHost.Services.GetRequiredService<IClusterClient>();
                return new WorkloadScheduler(clusterClient);
            });
        })
        .Build();


    // await host.RunAsync();
    
    var workloadCoordinator = new WorkloadCoordinator(host.Services.GetRequiredService<WorkloadScheduler>());

    // await workloadCoordinator.RunWorkload(workloadToRun, transactions, operations);
    await workloadCoordinator.ScheduleWorkload(workloadToRun, transactions, operations);

    Console.WriteLine("Workload generation finished. Press Enter to terminate");
    Console.ReadLine();
}


// var workloadCoordinator = new WorkloadCoordinator();
// // starts orleans cluster (silo) and orleans client
// await workloadCoordinator.Init();
//
// // do work, submit workload, gather results
// workloadCoordinator.StartExecution(100);
//
//
// // used to prevent orleans server exit immediately
// Console.WriteLine("*************************************************************************");
// Console.WriteLine("Workload Generation done, press Enter to terminate.");
// Console.WriteLine("*************************************************************************");
// Console.ReadLine();