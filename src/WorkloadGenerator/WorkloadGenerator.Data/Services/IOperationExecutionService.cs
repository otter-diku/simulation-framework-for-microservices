using WorkloadGenerator.Data.Models.Operation;

namespace WorkloadGenerator.Data.Services;

public interface IOperationExecutionService
{
    bool CanHandle(IOperationUnresolved operation);

    Task<Dictionary<string, object>> Execute(IOperationUnresolved unresolved,
        Dictionary<string, object> providedValues);
}