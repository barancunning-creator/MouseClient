using System;
using System.IO;
using System.Text.Json.Serialization;

namespace MouseLauncher.Models
{
    public class LauncherConfig
    {
        [JsonPropertyName("nickname")]
        public string Nickname { get; set; } = "MouseUser";

        [JsonPropertyName("ramMb")]
        public int RamMb { get; set; } = 4096;

        [JsonPropertyName("minecraftPath")]
        public string MinecraftPath { get; set; } = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            ".minecraft");

        [JsonPropertyName("javaPath")]
        public string JavaPath { get; set; } = "";

        [JsonPropertyName("autoClose")]
        public bool AutoClose { get; set; } = false;

        [JsonPropertyName("lastInstalledVersion")]
        public string LastInstalledVersion { get; set; } = "";

        [JsonPropertyName("customJvmArgs")]
        public string CustomJvmArgs { get; set; } = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled";
    }
}
