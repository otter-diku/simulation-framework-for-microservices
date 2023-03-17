namespace Utilities;

public static class Helper
{
    public static (List<(string FileName, string Content)>? Files, string? ErrorMessage) TryReadAllJsonFiles(string path)
    {
        try
        {
            var result = Directory
                .GetFiles(path, "*.json")
                .Select(file => (Path.GetFileName(file), File.ReadAllText(file)))
                .ToList();

            foreach (var directory in Directory.GetDirectories(path))
            {
                var (files, errorMessage) = TryReadAllJsonFiles(directory);
                if (errorMessage is not null)
                {
                    return (null, errorMessage);
                }

                result.AddRange(files!);
            }

            return (result, null);
        }
        catch (Exception e)
        {
            return (null, e.Message);
        }
    }
}