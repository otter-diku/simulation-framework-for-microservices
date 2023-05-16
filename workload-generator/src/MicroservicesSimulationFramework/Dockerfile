FROM mcr.microsoft.com/dotnet/runtime:7.0 AS base
WORKDIR /app

FROM mcr.microsoft.com/dotnet/sdk:7.0 AS build
WORKDIR /src
COPY ["MicroservicesSimulationFramework.Core/MicroservicesSimulationFramework.Core.csproj", "MicroservicesSimulationFramework.Core/"]
RUN dotnet restore "MicroservicesSimulationFramework.Core/MicroservicesSimulationFramework.Core.csproj"
COPY . .
WORKDIR "/src/MicroservicesSimulationFramework.Core"
RUN dotnet build "MicroservicesSimulationFramework.Core.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "MicroservicesSimulationFramework.Core.csproj" -c Release -o /app/publish

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "MicroservicesSimulationFramework.Core.dll"]
