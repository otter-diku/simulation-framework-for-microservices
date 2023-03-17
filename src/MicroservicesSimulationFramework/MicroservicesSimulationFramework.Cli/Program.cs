using MicroservicesSimulationFramework.Core.Models;
using MicroservicesSimulationFramework.Core.Services;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
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

        var (jsonFiles, errorMessage) = Helper.TryReadAllJsonFiles(args[0]);

        if (errorMessage is not null)
        {
            Console.WriteLine("Failed trying to read JSON configuration files:\n" +
                              errorMessage + "\n" +
                              "Exiting...");
            return;
        }

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

        var workloadSelected = SelectWorkload(scenarioValidated!.Workloads.Values.ToList());
        if (workloadSelected is null)
        {
            Console.WriteLine("No workload selected, exiting...");
            return;
        }

        Console.WriteLine($"\nSelected workload: {workloadSelected.TemplateId}.\n" +
                          "Scheduler starting...\n");
        var workloadCoordinator = host.Services.GetRequiredService<IWorkloadCoordinator>();

        await workloadCoordinator.ScheduleWorkload(
            workloadSelected,
            scenarioValidated.Transactions,
            scenarioValidated.Operations);

        Console.WriteLine("\nWorkload generation finished. Press any key to terminate");
        Console.ReadLine();
    }

    private static WorkloadGeneratorInputUnvalidated ExtractInputFiles(List<(string FileName, string Content)> valueTuples)
    {
        var filesSplit = valueTuples
            .GroupBy(file => file.FileName.Split("_").FirstOrDefault())
            .ToList();

        var operationFiles = filesSplit
            .SingleOrDefault(group => group.Key == "op")
            ?.ToList();

        var transactionFiles = filesSplit
            .SingleOrDefault(group => group.Key == "tx")
            ?.ToList();

        var workloadFiles = filesSplit
            .SingleOrDefault(group => group.Key == "workload")
            ?.ToList();

        return new WorkloadGeneratorInputUnvalidated(operationFiles!, transactionFiles!, workloadFiles!);
    }

    private static WorkloadInputUnresolved? SelectWorkload(List<WorkloadInputUnresolved> workloads)
    {
        Console.WriteLine("Select workload to run (or press 'q' to quit):");

        while (true)
        {
            var workloadNum = 1;
            foreach (var workload in workloads)
            {
                Console.WriteLine($"({workloadNum}) {workload.TemplateId}");
                workloadNum++;
            }

            var input = Console.ReadLine()?.Trim();

            if (input is "q" or "Q")
            {
                return null;
            }

            var selectedWithinRange = int.TryParse(input, out var selected)
                                      && selected > 0
                                      && selected <= workloads.Count;

            if (selectedWithinRange)
            {
                return workloads[selected - 1];
            }

            Console.WriteLine("\nInvalid workload number provided, try again. (or press 'q' to quit)\n");
        }
    }

    private static async Task<IHost> Configure(string[] strings)
    {
        var _ = await WorkloadGeneratorServer.StartSiloAsync();
        var orleansClientHost = await OrleansClientManager.StartClientAsync();
        var hostBuilder = Host.CreateDefaultBuilder(strings);

        return hostBuilder
            .ConfigureLogging((_, loggingBuilder) =>
            {
                loggingBuilder.ClearProviders();
                loggingBuilder.AddConsole().AddDebug();
            })
            .ConfigureServices(services =>
            {
                services.AddSingleton<TransactionRunnerService>();
                services.AddHttpClient<TransactionRunnerService>();
                services.AddSingleton<ITransactionOperationService, TransactionOperationService>();
                services.AddSingleton<ITransactionService, TransactionService>();
                services.AddSingleton<IWorkloadService, WorkloadService>();
                services.AddSingleton<IWorkloadCoordinator, WorkloadCoordinator>();
                services.AddSingleton<IWorkloadGeneratorRunnerService, WorkloadGeneratorRunnerService>();
                services.AddSingleton<IWorkloadScheduler, WorkloadScheduler>(_ =>
                {
                    var clusterClient = orleansClientHost.Services.GetRequiredService<IClusterClient>();
                    var loggerFactory = orleansClientHost.Services.GetRequiredService<ILoggerFactory>();
                    return new WorkloadScheduler(loggerFactory.CreateLogger<WorkloadScheduler>(), clusterClient);
                });
            })
            .Build();
    }
}