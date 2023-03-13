run_eshop_workloads:
	cd src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Core && dotnet run /home/pt/repos/simulation-framework-for-microservices/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/eShop


test_eshop:
	 dotnet test --no-restore --verbosity normal src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.sln
