using WorkloadGenerator.Data.Models.Operation;

namespace WorkloadGenerator.Data.Services;

public interface IOperationService
{
    bool TryParseInput(string json, out IOperationUnresolved unresolvedInput);

    bool TryResolve(IOperationUnresolved unresolvedInput,
        Dictionary<string, object> providedValues, out IOperationResolved resolvedInput);

    bool TryConvertToExecutable(IOperationResolved resolvedInput,
        out OperationExecutableBase operationBaseExecutable);
}