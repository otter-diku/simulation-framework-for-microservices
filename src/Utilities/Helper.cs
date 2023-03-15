namespace Utilities;

public static class Helper
{
    public static List<(string, string)> ReadAllJsonFiles(string path)
    {
        try
        {
            var result = Directory
                .GetFiles(path, "*.json")
                .Select(file => (Path.GetFileName(file), File.ReadAllText(file)))
                .ToList();
            
            foreach (var directory in Directory.GetDirectories(path))
            {
                result.AddRange(ReadAllJsonFiles(directory)!);
            }

            return result;
        }
        catch (Exception e)
        {
            Console.WriteLine(e.Message);
            throw;
        }
    }
}