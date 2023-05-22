run_eshop_workloads:
	cd workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/workload-generator/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/eShop

run_hosted_eshop_workloads:
	cd workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/example/workload-definitions/eshop-hosted

run_lakeside_workloads:
	cd workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.Cli && dotnet run $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/workload-generator/src/WorkloadGenerator/WorkloadGenerator.Data.Test/Integration/lakesideMutual


test_workload_generator:
	 dotnet test --no-restore --verbosity normal workload-generator/src/MicroservicesSimulationFramework/MicroservicesSimulationFramework.sln


build_invariant_translator:
	cd invariant-translator/ && mvn clean compile assembly:single


run_invariant_translator_eshop_invariants:
	java -jar invariant-translator/target/invariant-translator-0.1.jar \
	-invariants $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/example/invariant-queries/eshop \
	-queue-config $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/example/invariant-queries/eshop/kafkaConfig.json \
	-output $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))/example/output
