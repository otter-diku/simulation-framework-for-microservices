// See https://aka.ms/new-console-template for more information

using WorkloadGenerator.Coordinator;

var workloadCoordinator = new WorkloadCoordinator();
await workloadCoordinator.Init();

var xacts = workloadCoordinator.generateTranscationDistribution(100);


workloadCoordinator.StartExecution();

// do work, submit workload, gather results


// used to prevent orleans server to exit immediately
Console.ReadLine();