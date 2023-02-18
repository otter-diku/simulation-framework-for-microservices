using Microsoft.Extensions.Hosting;
using Orleans.Configuration;
using Utilities;

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
                        .UseDashboard(_ => { }); // localhost:8080
                });

            var server = builder.Build();
            await server.StartAsync();
            Console.WriteLine("\n *************************************************************************");
            Console.WriteLine("    The Workload Generator server started. Press Enter to terminate...");
            Console.WriteLine("\n *************************************************************************");
            return server;
        }
    }
}