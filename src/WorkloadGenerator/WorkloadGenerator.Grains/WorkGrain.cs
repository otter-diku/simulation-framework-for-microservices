using System.Collections.Concurrent;
using Microsoft.Extensions.Logging.Abstractions;
using Orleans.Concurrency;
using WorkloadGenerator.Data.Models;
using WorkloadGenerator.Data.Services;
using WorkloadGenerator.Grains.Interfaces;

namespace WorkloadGenerator.Grains;

public class WorkGrain : IWorkGrain
{
    private ConcurrentQueue<ExecutableTransaction> _transactionQueue;
    private TransactionRunnerService _runnerService;
    
    public Task Init(IHttpClientFactory httpClientFactory, ConcurrentQueue<ExecutableTransaction> transactionQueue)
    {
        var transactionOperationService =
            new TransactionOperationService(NullLogger<TransactionOperationService>.Instance);
        _runnerService = new TransactionRunnerService(
            transactionOperationService,
            httpClientFactory,
            NullLogger<TransactionRunnerService>.Instance
        );
        
        _transactionQueue = transactionQueue;
        return Task.CompletedTask;
    }

    // TODO: could make reentrant and once transaction queue is empty we can dispose the grain gracefully from scheduler
    public async void Start()
    {
        while (true)
        {
            if (_transactionQueue.TryDequeue(out var execTx))
            {
                await _runnerService.Run(execTx.Transaction, execTx.ProvidedValues, execTx.Operations);    
            }
        }
    }
}