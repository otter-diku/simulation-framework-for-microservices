using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Orleans.Configuration;
using Orleans.Serialization;
using Utilities;
using WorkloadGenerator.Data.Models.Operation;

namespace WorkloadGenerator.Client;

public static class OrleansClientManager
{
    public static async Task<IHost> StartClientAsync()
    {
        var builder = new HostBuilder()
            .ConfigureLogging((_, loggingBuilder) =>
            {
                loggingBuilder.ClearProviders();
                loggingBuilder.AddSeq();
            })
            .UseOrleansClient(clientBuilder =>
            {
                clientBuilder
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
                    .AddMemoryStreams("StreamProvider");

                clientBuilder.Services.AddSerializer(serializerBuilder =>
                {
                    serializerBuilder.AddJsonSerializer(
                        isSupported: type =>
                            type.Namespace!.StartsWith("WorkloadGenerator."),
                        Utilities.SerializerUtils.GetGlobalJsonSerializerOptions(opt =>
                        {
                            opt.Converters.Add(new IOperationUnresolvedJsonConverter());
                        }));
                });
            });

        var host = builder.Build();
        await host.StartAsync();
        return host;
    }
}