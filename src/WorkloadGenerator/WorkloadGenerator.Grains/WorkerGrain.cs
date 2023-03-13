using Microsoft.Extensions.Logging.Abstractions;
using Utilities;
using WorkloadGenerator.Data.Models.Generator;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Models.Workload;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Grains;

public class WorkerGrain : IWorkerGrain
{
    public async Task ExecuteTransaction(
        WorkloadInputUnresolved workload,
        TransactionReference txRef,
        TransactionInputUnresolved tx,
        Dictionary<string, ITransactionOperationUnresolved> operations,
        IHttpClientFactory httpClientFactory)
    {
        var transactionOperationService =
            new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        var runnerService = new TransactionRunnerService(
            transactionOperationService,
            httpClientFactory,
            NullLogger<TransactionRunnerService>.Instance
        );

        // generate provided values needed by transaction
        var providedValues = new Dictionary<string, object>();

        foreach (var genRef in txRef.Data)
        {
            var generatorInput = workload.Generators.First(g => g.Id == genRef.GeneratorReferenceId);
            var generator = GeneratorFactory.GetGenerator(generatorInput);
            providedValues.Add(genRef.Name, generator.Next());
        }

        await runnerService.Run(tx, providedValues, operations);


    }
}
