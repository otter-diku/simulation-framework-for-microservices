using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using Utilities;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Workload;

namespace WorkloadGenerator.Data.Services;

public class WorkloadService : IWorkloadService
{
    private readonly ILogger<WorkloadService> _logger;
    private readonly WorkloadInputUnresolvedValidator _workloadInputUnresolvedValidator;

    public WorkloadService(ILogger<WorkloadService> logger)
    {
        _logger = logger;
        _workloadInputUnresolvedValidator = new WorkloadInputUnresolvedValidator();
    }

    private readonly JsonSerializerOptions _jsonSerializerOptions = SerializerUtils.GetGlobalJsonSerializerOptions();

    public bool TryParseInput(string json, out WorkloadInputUnresolved workloadInputUnresolved)
    {
        workloadInputUnresolved = null!;
        try
        {
            workloadInputUnresolved =
                JsonSerializer.Deserialize<WorkloadInputUnresolved>(json, _jsonSerializerOptions);

            return workloadInputUnresolved is not null
                   && _workloadInputUnresolvedValidator.Validate(workloadInputUnresolved).IsValid;
        }
        catch (Exception exception)
        {
            _logger.LogInformation(exception,
                "Failed trying to deserialize input data for workload");

            return false;
        }
    }

    public bool TryResolve(
        WorkloadInputUnresolved unresolved,
        HashSet<string> transactionReferenceIds,
        out WorkloadInputResolved resolved)
    {
        resolved = null!;

        if (!ValidateTransactionReferenceIds(unresolved.Transactions, transactionReferenceIds))
        {
            return false;
        }

        resolved = new WorkloadInputResolved()
        {
            TemplateId = unresolved.TemplateId,
            TransactionReferences = unresolved.Transactions
                .ToDictionary(x => x.TransactionReferenceId, x => x),
            Generators = unresolved.Generators
        };

        return true;
    }

    private bool ValidateTransactionReferenceIds(List<TransactionReference> unresolvedTransactions,
        HashSet<string> transactionReferenceIds)
    {
        var unknownTransactionReference =
            unresolvedTransactions.FirstOrDefault(tx
                => !transactionReferenceIds.Contains(tx.TransactionReferenceId));

        if (unknownTransactionReference is null)
        {
            return true;
        }

        _logger.LogWarning(
            "{MethodName} failed: unknown operation reference {OperationReferenceId}",
            nameof(ValidateTransactionReferenceIds),
            unknownTransactionReference.TransactionReferenceId);

        return false;
    }


    private IGenerator CreateGenerator(GeneratorBase generatorBase)
    {
        switch (generatorBase.Type)
        {
            case GeneratorType.UnsignedInt:
                return new NumberGenerator(true);
            case GeneratorType.SignedInt:
                return new NumberGenerator(false);
            case GeneratorType.String:
                return new StringGenerator();
            case GeneratorType.Guid:
                return new GuidGenerator();
            default:
                throw new ArgumentOutOfRangeException();
        }
    }

    public Dictionary<string, object> GenerateData(WorkloadInputResolved workload, string transactionRefId)
    {
        var providedValues = new Dictionary<string, object>();

        var generators = new Dictionary<string, IGenerator>();
        if (workload.Generators is not null)
        {
            workload.Generators
                .ForEach(g => generators.Add(g.Id, CreateGenerator(g)));
        }
        var tx = workload.TransactionReferences
            .GetValueOrDefault(transactionRefId, null);

        // generate all values for the transaction
        if (tx is not null && tx.Data is not null)
        {
            foreach (var genRef in tx.Data)
            {
                providedValues.Add(tx.TransactionReferenceId,
                    generators[genRef.GeneratorReferenceId].Next());
            }
        }

        return providedValues;
    }
}