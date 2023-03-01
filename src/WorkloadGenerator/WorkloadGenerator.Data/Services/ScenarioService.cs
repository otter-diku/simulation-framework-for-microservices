using System.Xml.Schema;
using WorkloadGenerator.Data.Models.Scenario;

namespace WorkloadGenerator.Data.Services;

public class ScenarioService
{


    public void RunScenario(Scenario scenario)
    {
        
        // Generate variables
        var concreteVariables = new Dictionary<string, Variable>();
        foreach (var variable in scenario.variables)
        {
        }
        
        // transactions (fill with arguments, according to distributions)
        
        // submit transactions to runner grains
        
        
        // gather statistics
        
    }

}

public class ConcreteVariable
{
}