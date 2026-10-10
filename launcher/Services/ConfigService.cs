using System;
using System.IO;
using System.Text.Json;
using MouseLauncher.Models;

namespace MouseLauncher.Services
{
    public class ConfigService
    {
        private static readonly string ConfigDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "MouseLauncher");

        private static readonly string ConfigFilePath = Path.Combine(ConfigDir, "config.json");

        private static readonly JsonSerializerOptions JsonOptions = new()
        {
            WriteIndented = true,
            PropertyNameCaseInsensitive = true
        };

        public LauncherConfig Load()
        {
            try
            {
                if (!Directory.Exists(ConfigDir))
                {
                    Directory.CreateDirectory(ConfigDir);
                }

                if (File.Exists(ConfigFilePath))
                {
                    string json = File.ReadAllText(ConfigFilePath);
                    var config = JsonSerializer.Deserialize<LauncherConfig>(json, JsonOptions);
                    if (config != null)
                    {
                        return config;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[ConfigService] Error loading config: {ex.Message}");
            }

            var defaultConfig = new LauncherConfig();
            Save(defaultConfig);
            return defaultConfig;
        }

        public void Save(LauncherConfig config)
        {
            try
            {
                if (!Directory.Exists(ConfigDir))
                {
                    Directory.CreateDirectory(ConfigDir);
                }

                string json = JsonSerializer.Serialize(config, JsonOptions);
                File.WriteAllText(ConfigFilePath, json);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[ConfigService] Error saving config: {ex.Message}");
            }
        }
    }
}
