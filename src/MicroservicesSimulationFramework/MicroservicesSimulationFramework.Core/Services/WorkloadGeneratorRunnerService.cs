using MicroservicesSimulationFramework.Core.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Services;

namespace MicroservicesSimulationFramework.Core.Services;

public class WorkloadGeneratorRunnerService : IWorkloadGeneratorRunnerService
{
    private readonly ITransactionOperationService _transactionOperationService;
    private readonly ITransactionService _transactionService;
    private readonly IWorkloadService _workloadService;

    public WorkloadGeneratorRunnerService(
        ITransactionOperationService transactionOperationService,
        ITransactionService transactionService,
        IWorkloadService workloadService)
    {
        _transactionOperationService = transactionOperationService;
        _transactionService = transactionService;
        _workloadService = workloadService;
    }

    public (WorkloadGeneratorInputValidated? ScenarioValidated, string? ErrorMessage) TryValidate(WorkloadGeneratorInputUnvalidated workloadGeneratorInputUnvalidated)
    {
        if (workloadGeneratorInputUnvalidated.Operations is { Count: > 0 }
            && workloadGeneratorInputUnvalidated.Transactions is { Count: > 0 }
            && workloadGeneratorInputUnvalidated.Workloads is { Count: > 0 })
        {
            return TryGetValidatedScenario(workloadGeneratorInputUnvalidated);
        }

        var errorMessage = "Did not find all the necessary files.\n" +
                           $"Operation files found: {workloadGeneratorInputUnvalidated.Operations?.Count ?? 0}\n" +
                           $"Transaction files found: {workloadGeneratorInputUnvalidated.Transactions?.Count ?? 0}\n" +
                           $"Workload files found: {workloadGeneratorInputUnvalidated.Workloads?.Count ?? 0}\n";

        return (null, errorMessage);
    }

    private (WorkloadGeneratorInputValidated? ScenarioValidated, string? ErrorMessage) TryGetValidatedScenario(WorkloadGeneratorInputUnvalidated workloadGeneratorInputUnvalidated)
    {
        var scenarioValidated = new WorkloadGeneratorInputValidated(
            new Dictionary<string, ITransactionOperationUnresolved>(),
            new Dictionary<string, TransactionInputUnresolved>(),
            new Dictionary<string, WorkloadInputUnresolved>());

        foreach (var (fileName, content) in workloadGeneratorInputUnvalidated.Operations)
        {
            var parsingResult = _transactionOperationService.TryParseInput(content, out var parsedOperation);
            if (!parsingResult)
            {
                return (null, $"Error while parsing {fileName}");
            }

            scenarioValidated.Operations.Add(parsedOperation.TemplateId, parsedOperation);
        }

        foreach (var (fileName, content) in workloadGeneratorInputUnvalidated.Transactions)
        {
            var parsingResult = _transactionService.TryParseInput(content, out var parsedTransaction);
            if (!parsingResult)
            {
                return (null, $"Error while parsing {fileName}");
            }

            scenarioValidated.Transactions.Add(parsedTransaction.TemplateId, parsedTransaction);
        }

        foreach (var (fileName, content) in workloadGeneratorInputUnvalidated.Workloads)
        {
            var parsingResult = _workloadService.TryParseInput(content, out var parsedWorkload);
            if (!parsingResult)
            {
                return (null, $"Error while parsing {fileName}");
            }

            scenarioValidated.Workloads.Add(parsedWorkload.TemplateId, parsedWorkload);
        }

        return (scenarioValidated, null);
    }
}