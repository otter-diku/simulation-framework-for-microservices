using System.Collections.Concurrent;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Services;
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
    private int _maxConcurrentTransactions;
    private IClusterClient _client;
    private ConcurrentQueue<ExecutableTransaction> _transactionQueue;
    private IHttpClientFactory _httpClientFactory;

    public WorkloadScheduler(int maxConcurrentTransactions, IClusterClient client, IHttpClientFactory httpClientFactory)
    {
        _maxConcurrentTransactions = maxConcurrentTransactions;
        _client = client;
        _httpClientFactory = httpClientFactory; 
        
        _transactionQueue = new ConcurrentQueue<ExecutableTransaction>();
    }

    public async void Init()
    {
        for (int i = 0; i < _maxConcurrentTransactions; i++)
        {
            var worker = _client.GetGrain<IWorkGrain>(i,
                grainClassNamePrefix: "WorkloadGenerator.Grains.WorkGrain");
            
            await worker.Init(_httpClientFactory, _transactionQueue);
            worker.Start();
        }
    }
    
    public void SubmitTransaction(ExecutableTransaction executableTransaction)
    {
        _transactionQueue.Enqueue(executableTransaction);
    }

    public Task WaitForEmptyQueue()
    {
        while (_transactionQueue.Count != 0)
        {
        }

        return Task.CompletedTask;
    }


}