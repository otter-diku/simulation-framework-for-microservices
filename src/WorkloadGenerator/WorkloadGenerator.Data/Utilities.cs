using AutoFixture;
using WorkloadGenerator.Data.Models;

namespace WorkloadGenerator.Data;

public class Utilities
{
    public static bool ValidateArguments(Argument[]? arguments,
        IReadOnlyDictionary<string, object>? providedValues)
    {
        return arguments is null ||
               arguments.All(requiredArgument =>
                   providedValues?.ContainsKey(requiredArgument.Name) ?? false);
    }
    
    public static Dictionary<string, object> AddDynamicValues(DynamicVariable[]? dynamicVariables,
        Dictionary<string, object> providedValues)
    {
        if (dynamicVariables is null)
        {
            return providedValues;
        }

        var concreteVariables = GenerateDynamicVariables(dynamicVariables);
        foreach (var kv in concreteVariables)
        {
            providedValues.Add(kv.Key, kv.Value);
        }

        return providedValues;
    }

    private static Dictionary<string, object> GenerateDynamicVariables(DynamicVariable[] dynamicVariables)
    {
        var fixture = new Fixture();
        return dynamicVariables.ToDictionary(dynamicVar => dynamicVar.Name, dynamicVar =>
        {
            return dynamicVar.Type switch
            {
                DynamicVariableType.UnsignedInt => fixture.Create<uint>(),
                DynamicVariableType.SignedInt => fixture.Create<bool>()
                    ? fixture.Create<int>()
                    : -1 * fixture.Create<int>(),
                DynamicVariableType.String => (object)fixture.Create<string>(),
                DynamicVariableType.Guid => Guid.NewGuid(),
                _ => throw new ArgumentOutOfRangeException()
            };
        });
    }
}