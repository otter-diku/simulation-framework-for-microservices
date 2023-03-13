using System.Collections.Concurrent;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Models.Transaction;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Grains.Interfaces;

public interface IWorkGrain : IGrainWithIntegerKey
{
    public Task Init(IHttpClientFactory httpClientFactory, ConcurrentQueue<ExecutableTransaction> transactionQueue);

    public Task Start();
}