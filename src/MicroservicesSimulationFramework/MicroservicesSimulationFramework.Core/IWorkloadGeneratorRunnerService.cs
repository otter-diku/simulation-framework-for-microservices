using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Services;

namespace MicroservicesSimulationFramework.Core;

public interface IWorkloadGeneratorRunnerService
{
    public (ScenarioValidated? ScenarioValidated, string? ErrorMessage) TryValidate(ScenarioInput scenarioInput);
}

public class WorkloadGeneratorRunnerService
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

    public (ScenarioValidated? ScenarioValidated, string? ErrorMessage) TryValidate(ScenarioInput scenarioInput)
    {
        if (scenarioInput.Operations is { Count: > 0 }
            && scenarioInput.Transactions is { Count: > 0 }
            && scenarioInput.Workloads is { Count: > 0 })
        {
            return TryGetValidatedScenario(scenarioInput);
        }

        var errorMessage = "Did not find all the necessary files.\n" +
                           $"Operation files found: {scenarioInput.Operations?.Count ?? 0}\n" +
                           $"Transaction files found: {scenarioInput.Transactions?.Count ?? 0}\n" +
                           $"Workload files found: {scenarioInput.Workloads?.Count ?? 0}\n";

        return (null, errorMessage);
    }

    private (ScenarioValidated? ScenarioValidated, string? ErrorMessage) TryGetValidatedScenario(ScenarioInput scenarioInput)
    {
        var scenarioValidated = new ScenarioValidated(
            new Dictionary<string, ITransactionOperationUnresolved>(),
            new Dictionary<string, TransactionInputUnresolved>(),
            new Dictionary<string, WorkloadInputUnresolved>());

        foreach (var (fileName, content) in scenarioInput.Operations)
        {
            var parsingResult = _transactionOperationService.TryParseInput(content, out var parsedOperation);
            if (!parsingResult)
            {
                return (null, $"Error while parsing {fileName}");
            }

            scenarioValidated.Operations.Add(parsedOperation.TemplateId, parsedOperation);
        }

        foreach (var (fileName, content) in scenarioInput.Transactions)
        {
            var parsingResult = _transactionService.TryParseInput(content, out var parsedTransaction);
            if (!parsingResult)
            {
                return (null, $"Error while parsing {fileName}");
            }

            scenarioValidated.Transactions.Add(parsedTransaction.TemplateId, parsedTransaction);
        }

        foreach (var (fileName, content) in scenarioInput.Workloads)
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