using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace MouseLauncher.Models
{
    public class ClientVersionInfo
    {
        [JsonPropertyName("version")]
        public string Version { get; set; } = "1.0.0";

        [JsonPropertyName("minecraftVersion")]
        public string MinecraftVersion { get; set; } = "1.21.11";

        [JsonPropertyName("fabricVersion")]
        public string FabricVersion { get; set; } = "0.19.5";

        [JsonPropertyName("downloadUrl")]
        public string DownloadUrl { get; set; } = "";

        [JsonPropertyName("jarName")]
        public string JarName { get; set; } = "MouseClient-1.0.0.jar";

        [JsonPropertyName("sha256")]
        public string Sha256 { get; set; } = "";

        [JsonPropertyName("changelog")]
        public List<string> Changelog { get; set; } = new();

        [JsonPropertyName("notice")]
        public string Notice { get; set; } = "Приятной игры с MouseClient!";

        [JsonPropertyName("githubRepo")]
        public string GithubRepo { get; set; } = "barancunning-creator/MouseClient";

        [JsonPropertyName("telegramUrl")]
        public string TelegramUrl { get; set; } = "";

        [JsonPropertyName("discordUrl")]
        public string DiscordUrl { get; set; } = "";
    }
}
