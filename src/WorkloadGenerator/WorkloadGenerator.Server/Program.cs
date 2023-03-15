using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Hosting;
using Orleans.Configuration;
using Orleans.Serialization;
using Utilities;
using WorkloadGenerator.Data.Models.Operation;

namespace WorkloadGenerator.Server
{
    public static class WorkloadGeneratorServer
    {
        public static async Task<IHost> StartSiloAsync()
        {
            var builder = new HostBuilder()
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
                        .AddMemoryStreams("StreamProvider")
                        .AddMemoryGrainStorage("PubSubStore")
                        .UseDashboard(_ => { }); // localhost:8080
                    siloBuilder.Services.AddSerializer(serializerBuilder =>
                    {
                        serializerBuilder.AddJsonSerializer(
                            isSupported: type =>
                                type.Namespace.StartsWith("WorkloadGenerator."),

                            new JsonSerializerOptions(new JsonSerializerOptions()
                            {
                                PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
                                PropertyNameCaseInsensitive = true,
                                Converters =
                                {
                                    new JsonStringEnumConverter(JsonNamingPolicy.CamelCase),
                                    new ITransactionOperationUnresolvedJsonConverter()
                                }
                            })
                            );
                    });
                });

            var server = builder.Build();
            await server.StartAsync();
            return server;
        }
    }
}
