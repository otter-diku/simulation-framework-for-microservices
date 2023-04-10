run_eshop_workloads:
	cd src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/eShop

run_lakeside_workloads:
	cd src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/lakesideMutual


test_eshop:
	 dotnet test --no-restore --verbosity normal src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.sln
