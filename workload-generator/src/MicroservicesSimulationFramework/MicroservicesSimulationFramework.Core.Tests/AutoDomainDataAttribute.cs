using AutoFixture;
using AutoFixture.AutoMoq;
using AutoFixture.NUnit3;

namespace MicroservicesSimulationFramework.Core.Tests;

public class AutoDomainDataAttribute : AutoDataAttribute
{
    public AutoDomainDataAttribute()
        :base(() => new Fixture().Customize(new AutoMoqCustomization()))
    {
    }
}