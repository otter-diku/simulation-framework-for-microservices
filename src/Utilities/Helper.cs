namespace Utilities;

public static class Helper
{
    public static List<(string,string)>? ReadAllJsonFiles(List<(string,string)> files, string path)
    {
        try
        {
            foreach (string file in Directory.GetFiles(path, "*.json")) {
                // Do something with the JSON file
                
                files.Add((Path.GetFileName(file), File.ReadAllText(file)));
            }

            foreach (string dir in Directory.GetDirectories(path)) {
                ReadAllJsonFiles(files, dir);
            }

        }
        catch (Exception e)
        {
            Console.WriteLine(e.Message);
            return null;
        }

        return files;
    }
    
}