using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Orleans.Configuration;
using Orleans.Serialization;
using Utilities;
using WorkloadGenerator.Data.Models.Operation;
using WorkloadGenerator.Data.Services;

namespace WorkloadGenerator.Server
{
    public static class WorkloadGeneratorServer
    {
        public static async Task<IHost> StartSiloAsync()
        {
            var builder = new HostBuilder()
                .ConfigureLogging((_, loggingBuilder) =>
                {
                    loggingBuilder.ClearProviders();
                    loggingBuilder.AddSeq();
                })
                .UseOrleans(siloBuilder =>
                {
                    siloBuilder
                        .UseLocalhostClustering() // the silo membership table will be maintained in-memory
                        .Configure<ClusterOptions>(options =>
                        {
                            options.ClusterId = Constants.ClusterId;
                            options.ServiceId = Constants.ServiceId;
                        })
                        .Configure<EndpointOptions>(options =>
                        {
                            options.SiloPort = Constants.SiloPort; // silo-to-silo communication
                            options.GatewayPort = Constants.GatewayPort; // client-to-silo communication
                        })
                        .ConfigureLogging(loggingBuilder =>
                        {
                            loggingBuilder.ClearProviders();
                            loggingBuilder.AddSeq();
                        })
                        .ConfigureServices((sc) =>
                        {
                            sc.AddHttpClient<TransactionRunnerService>();
                            sc.AddSingleton<TransactionRunnerService>();
                            sc.AddSingleton<IOperationService, OperationService>();
                        })
                        .AddMemoryStreams("StreamProvider")
                        .AddMemoryGrainStorage("PubSubStore")
                        .UseDashboard(_ => { }); // localhost:8080
                    siloBuilder.Services.AddSerializer(serializerBuilder =>
                    {
                        serializerBuilder.AddJsonSerializer(
                            isSupported: type =>
                                type.Namespace!.StartsWith("WorkloadGenerator."),
                            SerializerUtils.GetGlobalJsonSerializerOptions(opt =>
                            {
                                opt.Converters.Add(new IOperationUnresolvedJsonConverter());
                            }));
                    });
                });

            var server = builder.Build();
            await server.StartAsync();
            return server;
        }
    }
}
