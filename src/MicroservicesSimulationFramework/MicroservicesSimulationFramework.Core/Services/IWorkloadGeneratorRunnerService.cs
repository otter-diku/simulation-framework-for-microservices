using MicroservicesSimulationFramework.Core.Models;

namespace MicroservicesSimulationFramework.Core.Services;

public interface IWorkloadGeneratorRunnerService
{
    public (WorkloadGeneratorInputValidated? ScenarioValidated, string? ErrorMessage) TryValidate(WorkloadGeneratorInputUnvalidated workloadGeneratorInputUnvalidated);
}