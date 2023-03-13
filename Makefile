run_eshop_workloads:
	cd src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Core && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/eShop


test_eshop:
	 dotnet test --no-restore --verbosity normal src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.sln
