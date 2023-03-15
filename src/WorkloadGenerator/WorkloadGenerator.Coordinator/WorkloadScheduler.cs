using System.Collections.Concurrent;
using Orleans.Runtime;
using Orleans.Streams;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Coordinator;

/// <summary>
/// The scheduler takes transactions and submits them to worker grains,
/// to control the rate of transactions per seconds we have a constant number
/// of worker grains. If there are no free worker grains the scheduler will
/// put the new transaction into a queue.
/// Once a worker grains is finished with its current transaction it
/// retrieves the next transaction from the queue.
/// </summary>
public class WorkloadScheduler
{
    private readonly int _maxConcurrentTransactions;
    private readonly IClusterClient _client;
    private readonly IHttpClientFactory _httpClientFactory;
    private ConcurrentDictionary<IWorkGrain, bool> _availableWorkers = new();
    private Dictionary<long, IAsyncStream<ExecutableTransaction>> _streams = new();

    public WorkloadScheduler(int maxConcurrentTransactions, IClusterClient client, IHttpClientFactory httpClientFactory)
    {
        _maxConcurrentTransactions = maxConcurrentTransactions;
        _client = client;
        _httpClientFactory = httpClientFactory;
    }

    public async Task Init()
    {
        var streamProvider = _client.GetStreamProvider("StreamProvider");

        for (var i = 0; i < _maxConcurrentTransactions; i++)
        {
            var worker = _client.GetGrain<IWorkGrain>(i,
                grainClassNamePrefix: "WorkloadGenerator.Grains.WorkGrain");

            await worker.Init(_httpClientFactory);
            _availableWorkers.TryAdd(worker, true);

            var stream =
                streamProvider.GetStream<ExecutableTransaction>(StreamId.Create("TRANSACTIONDATA", i.ToString()));
            _streams.Add(i, stream);
        }
    }

    public async Task SubmitTransaction(ExecutableTransaction executableTransaction)
    {
        var availableWorker = _availableWorkers.First(w => w.Value);
        // TODO: will only make sense once we have 2-way communication
        // _availableWorkers[availableWorker.Key] = false;

        var stream = _streams[availableWorker.Key.GetPrimaryKeyLong()];

        // TODO: remove

        var handles = await stream.GetAllSubscriptionHandles();
        foreach (var handle in handles)
        {
            Console.WriteLine(handle);
        }

        await stream.OnNextAsync(executableTransaction);
    }

    public Task WaitForEmptyQueue()
    {
        // while (_transactionQueue.Count != 0)
        // {
        // }

        return Task.CompletedTask;
    }
}