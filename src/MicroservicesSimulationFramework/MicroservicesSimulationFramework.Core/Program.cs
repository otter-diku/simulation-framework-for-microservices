// See https://aka.ms/new-console-template for more information

using WorkloadGenerator.Coordinator;

var workloadCoordinator = new WorkloadCoordinator();

// starts orleans cluster (silo) and orleans client
await workloadCoordinator.Init();

// do work, submit workload, gather results
workloadCoordinator.StartExecution(50);


// used to prevent orleans server exit immediately
Console.WriteLine("*************************************************************************");
Console.WriteLine("Workload Generation done, press Enter to terminate.");
Console.WriteLine("*************************************************************************");
Console.ReadLine();