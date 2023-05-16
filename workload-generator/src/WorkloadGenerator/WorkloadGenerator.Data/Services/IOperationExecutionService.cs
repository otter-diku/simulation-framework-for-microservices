using WorkloadGenerator.Data.Models.Operation;

namespace WorkloadGenerator.Data.Services;

public interface IOperationExecutionService
{
    Task<Dictionary<string, object>> Execute(IOperationResolved resolved);
}