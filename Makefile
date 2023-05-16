run_eshop_workloads:
	cd workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/workload-generator/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/eShop

run_lakeside_workloads:
	cd workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/workload-generator/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/lakesideMutual


test_workload_generator:
	 dotnet test --no-restore --verbosity normal workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.sln
