using Microsoft.Extensions.Logging;
using Orleans.Runtime;
using Orleans.Streams;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Grains;

[ImplicitStreamSubscription(StreamNamespace)]
public class WorkGrain : Grain, IWorkGrain
{
    private const string StreamNamespace = "TRANSACTIONDATA";
    private const string StreamProviderName = "StreamProvider";

    private readonly TransactionRunnerService _runnerService;
    private readonly ILogger<WorkGrain> _logger;

    public WorkGrain(TransactionRunnerService transactionRunnerService, ILogger<WorkGrain> logger)
    {
        _runnerService = transactionRunnerService;
        _logger = logger;
    }

    public override async Task OnActivateAsync(CancellationToken cancellationToken)
    {
        using var _ = _logger.BeginScope(new Dictionary<string, object>
        {
            { "GrainPrimaryKey", this.GetPrimaryKeyLong() },
            { "MethodName", nameof(OnActivateAsync) }
        });

        try
        {
            var streamProvider = this.GetStreamProvider(StreamProviderName);
            var streamId = StreamId.Create(StreamNamespace, this.GetPrimaryKeyLong().ToString());
            var stream = streamProvider.GetStream<ExecutableTransaction>(streamId);
            var handle = await stream.SubscribeAsync(Run);
            _logger.LogInformation("Subscribed to stream {Stream}. Handle: {HandleId}",
                StreamNamespace,
                handle.HandleId);
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, "Failed trying to subscribe to {Stream}", StreamNamespace);
        }
    }

    private async Task Run(ExecutableTransaction executableTransaction, StreamSequenceToken? token = null)
    {
        using var _ = _logger.BeginScope(new Dictionary<string, object>
        {
            { "WorkloadCorrelationId", executableTransaction.WorkloadCorrelationId },
            { "GrainPrimaryKey", this.GetPrimaryKeyLong() },
            { "MethodName", nameof(Run) },
            { "TransactionTemplateId", executableTransaction.Transaction.TemplateId },
            { "TransactionCorrelationId", Guid.NewGuid() /* TODO: correlation IDs should be hierarchical and passed down */},
        });

        _logger.LogInformation("Starting to execute transaction");

        try
        {
            await _runnerService.Run(executableTransaction.Transaction, executableTransaction.ProvidedValues,
                executableTransaction.Operations);
            _logger.LogInformation("Finished executing transaction");
        }
        catch (Exception exception)
        {
            _logger.LogWarning(exception, "Failed trying to execute transaction");
        }
    }
}