using System.Collections.Concurrent;
using Orleans.Runtime;
using Orleans.Streams;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Client;

/// <summary>
/// The scheduler takes transactions and submits them to worker grains,
/// to control the rate of transactions per seconds we have a constant number
/// of worker grains. If there are no free worker grains the scheduler will
/// put the new transaction into a queue.
/// Once a worker grains is finished with its current transaction it
/// retrieves the next transaction from the queue.
/// </summary>
public class WorkloadScheduler : IWorkloadScheduler
{
    private readonly IClusterClient _client;
    private ConcurrentDictionary<IWorkGrain, bool> _availableWorkers = new();
    private Dictionary<long, IAsyncStream<ExecutableTransaction>> _streams = new();

    public WorkloadScheduler(IClusterClient client)
    {
        _client = client;
    }

    public void Init(int maxConcurrentTransactions)
    {
        _availableWorkers = new ConcurrentDictionary<IWorkGrain, bool>();
        _streams = new Dictionary<long, IAsyncStream<ExecutableTransaction>>();

        var streamProvider = _client.GetStreamProvider("StreamProvider");

        for (var i = 0; i < maxConcurrentTransactions; i++)
        {
            var worker = _client.GetGrain<IWorkGrain>(i,
                grainClassNamePrefix: "WorkloadGenerator.Grains.WorkGrain");

            _availableWorkers.TryAdd(worker, true);

            var stream =
                streamProvider.GetStream<ExecutableTransaction>(StreamId.Create("TRANSACTIONDATA", i.ToString()));
            _streams.Add(i, stream);
        }
    }

    public async Task SubmitTransaction(ExecutableTransaction executableTransaction)
    {
        var availableWorker = _availableWorkers.MaxBy(_ => Guid.NewGuid());

        // TODO: will only make sense once we have 2-way communication
        // _availableWorkers[availableWorker.Key] = false;

        var stream = _streams[availableWorker.Key.GetPrimaryKeyLong()];

        await stream.OnNextAsync(executableTransaction);
    }
}