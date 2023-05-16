using System.Text.Json;


namespace WorkloadGenerator.Data.Test;

public class ScratchTest
{
    [Test]
    public void Test()
    {

        var x = Enumerable.Range(0, 75);
        var y = Enumerable.Range(75, 25);
        var input =
        """
        [
          {
            "name": "name-1",
            "price": 42 
          },
          {
             "name": "name-2",
             "price": 44
          }  
        ]
        """;
        var jsonDocument = JsonDocument.Parse(input);
        var obj = jsonDocument.RootElement.SelectElement("$[0].name");
    }
}