using MicroservicesSimulationFramework.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Utilities;
using WorkloadGenerator.Client;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Server;

namespace MicroservicesSimulationFramework.Cli;

public static class Program
{
    public static async Task Main(string[] args)
    {
        var host = await Configure(args);

        if (args.Length == 0)
        {
            Console.WriteLine("No workload configuration folder specified, exiting...");
            return;
        }

        var jsonFiles = Helper.ReadAllJsonFiles(args[0]);

        if (jsonFiles is not { Count: > 0 })
        {
            Console.WriteLine($"No JSON configuration files found at {args[0]}, exiting...");
            return;
        }

        var scenarioInput = ExtractInputFiles(jsonFiles);
        var workloadGeneratorRunnerService = host.Services.GetRequiredService<IWorkloadGeneratorRunnerService>();
        var (scenarioValidated, errorResult) = workloadGeneratorRunnerService.TryValidate(scenarioInput);
        if (errorResult is not null)
        {
            Console.Write("Failed trying to validate the input.\n" +
                          errorResult +
                          "Exiting...");
        }

        var workloadSelected = SelectWorkload(scenarioValidated);
        if (workloadSelected is null)
        {
            Console.WriteLine("No workload selected, exiting...");
        }

        Console.WriteLine($"Selected workload: {workloadSelected.TemplateId}.\nScheduler starting...");
        var workloadCoordinator = host.Services.GetRequiredService<IWorkloadCoordinator>();

        await workloadCoordinator.ScheduleWorkload(
            workloadSelected, 
            scenarioValidated.Transactions,
            scenarioValidated.Operations);

        Console.WriteLine("Workload generation finished. Press any key to terminate");
        Console.ReadLine();
    }
    
    private static ScenarioInput ExtractInputFiles(List<(string FileName, string Content)> valueTuples)
    {
        var filesSplit = valueTuples
            .GroupBy(file => file.FileName.Split("_").FirstOrDefault());

        var operationFiles = filesSplit
            .SingleOrDefault(group => group.Key == "op")
            ?.ToList();

        var transactionFiles = filesSplit
            .SingleOrDefault(group => group.Key == "tx")
            ?.ToList();

        var workloadFiles = filesSplit
            .SingleOrDefault(group => group.Key == "workload")
            ?.ToList();

        return new ScenarioInput(operationFiles!, transactionFiles!, workloadFiles!);
    }
    
    private static WorkloadInputUnresolved? SelectWorkload(ScenarioValidated scenario)
    {
        Console.WriteLine("Select workload to run:");

        while(true)
        {
            var workloadNum = 1;
            foreach (var (workloadTemplateId, _) in scenario.Workloads)
            {
                Console.WriteLine($"({workloadNum}) {workloadTemplateId}");
                workloadNum++;
            }

            var input = Console.ReadLine()?.Trim();

            if (input is "q" or "Q")
            {
                return null;
            }

            var parseResult = int.TryParse(input, out var selected) && selected <= scenario.Workloads.Count;

            if (!parseResult)
            {
                Console.WriteLine("Invalid workload number provided, try again. (or press 'q' to quit)\n");
            }
        }
    }

    private static async Task<IHost> Configure(string[] strings)
    {
        var _ = await WorkloadGeneratorServer.StartSiloAsync();
        var orleansClientHost = await OrleansClientManager.StartClientAsync();
        var hostBuilder = Host.CreateDefaultBuilder(strings);
        return hostBuilder.ConfigureServices(services =>
            {
                services.AddSingleton<TransactionRunnerService>();
                services.AddHttpClient<TransactionRunnerService>();
                services.AddSingleton<ITransactionOperationService, TransactionOperationService>();
                services.AddSingleton<ITransactionService, TransactionService>();
                services.AddSingleton<IWorkloadService, WorkloadService>();
                services.AddSingleton<IWorkloadCoordinator, WorkloadCoordinator>();
                services.AddSingleton<IWorkloadScheduler, WorkloadScheduler>(_ =>
                {
                    var clusterClient = orleansClientHost.Services.GetRequiredService<IClusterClient>();
                    return new WorkloadScheduler(clusterClient);
                });
            })
            .Build();
    }
}