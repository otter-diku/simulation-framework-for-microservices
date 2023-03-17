using Microsoft.Extensions.Logging.Abstractions;
using Orleans.Runtime;
using Orleans.Streams;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Grains;

[ImplicitStreamSubscription("TRANSACTIONDATA")]
public class WorkGrain : Grain, IWorkGrain
{
    private readonly TransactionRunnerService _runnerService;

    public WorkGrain(TransactionRunnerService transactionRunnerService)
    {
        _runnerService = transactionRunnerService;
    }

    public override async Task OnActivateAsync(CancellationToken cancellationToken)
    {
        var streamProvider = this.GetStreamProvider("StreamProvider");
        var streamId = StreamId.Create("TRANSACTIONDATA", this.GetPrimaryKeyLong().ToString());
        var stream = streamProvider.GetStream<ExecutableTransaction>(streamId);
        await stream.SubscribeAsync(Run);
    }

    private async Task Run(ExecutableTransaction executableTransaction, StreamSequenceToken? token = null)
    {
        try
        {
            Console.WriteLine(
                $"Grain {this.GetPrimaryKeyLong()} started {executableTransaction.Transaction.TemplateId} at {DateTimeOffset.Now}");
            await _runnerService.Run(executableTransaction.Transaction, executableTransaction.ProvidedValues,
                executableTransaction.Operations);
            Console.WriteLine(
                $"Grain {this.GetPrimaryKeyLong()} finished {executableTransaction.Transaction.TemplateId} at {DateTimeOffset.Now}");
        }
        catch (Exception e)
        {
            Console.WriteLine($"Failed, but returning as a hero. Exception: {e.Message}");
        }

    }
}