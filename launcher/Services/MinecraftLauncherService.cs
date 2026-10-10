using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using MouseLauncher.Models;

namespace MouseLauncher.Services
{
    public class LaunchResult
    {
        public bool Success { get; set; }
        public string Message { get; set; } = "";
        public Process? GameProcess { get; set; }
    }

    public class MinecraftLauncherService
    {
        private readonly JavaDetector _javaDetector;
        private readonly HttpClient _httpClient;

        public MinecraftLauncherService()
        {
            _javaDetector = new JavaDetector();
            _httpClient = new HttpClient();
            _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("MouseClient-Launcher/1.0.0");
        }

        public async Task<bool> EnsureFabricInstalledAsync(
            string minecraftPath,
            string mcVersion = "1.21.11",
            string loaderVersion = "0.19.5",
            IProgress<string>? statusProgress = null,
            CancellationToken cancellationToken = default)
        {
            try
            {
                string versionId = $"fabric-loader-{loaderVersion}-{mcVersion}";
                string versionDir = Path.Combine(minecraftPath, "versions", versionId);
                string versionJsonFile = Path.Combine(versionDir, $"{versionId}.json");

                if (File.Exists(versionJsonFile))
                {
                    statusProgress?.Report("Fabric Loader найден.");
                    return true;
                }

                statusProgress?.Report("Загрузка профиля Fabric с сервера...");
                Directory.CreateDirectory(versionDir);

                string metaUrl = $"https://meta.fabricmc.net/v2/versions/loader/{mcVersion}/{loaderVersion}/profile/json";
                using var response = await _httpClient.GetAsync(metaUrl, cancellationToken);
                response.EnsureSuccessStatusCode();

                string jsonContent = await response.Content.ReadAsStringAsync(cancellationToken);
                await File.WriteAllTextAsync(versionJsonFile, jsonContent, cancellationToken);

                // Загружаем необходимые библиотеки Fabric в libraries/
                statusProgress?.Report("Проверка библиотек Fabric Loader...");
                await DownloadFabricLibrariesAsync(minecraftPath, jsonContent, cancellationToken);

                statusProgress?.Report("Fabric успешно установлен!");
                return true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[MinecraftLauncherService] Fabric setup failed: {ex.Message}");
                return false;
            }
        }

        public async Task EnsureFabricApiAsync(string minecraftPath, IProgress<string>? statusProgress = null, CancellationToken cancellationToken = default)
        {
            try
            {
                string modsDir = Path.Combine(minecraftPath, "mods");
                if (!Directory.Exists(modsDir)) Directory.CreateDirectory(modsDir);

                var existing = Directory.GetFiles(modsDir, "*fabric-api*.jar");
                if (existing.Length > 0) return;

                statusProgress?.Report("Установка Fabric API...");
                string target = Path.Combine(modsDir, "fabric-api-0.141.6+1.21.11.jar");

                // Проверяем локальный кэш gradle
                string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
                string gradleCache = Path.Combine(userProfile, ".gradle", "caches", "modules-2", "files-2.1",
                    "net.fabricmc.fabric-api", "fabric-api", "0.141.6+1.21.11", "c98467cbbaf4d197377266795ae015f4130d65b6",
                    "fabric-api-0.141.6+1.21.11.jar");

                if (File.Exists(gradleCache))
                {
                    File.Copy(gradleCache, target, true);
                    return;
                }

                // Скачиваем с Fabric Maven
                string downloadUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.141.6+1.21.11/fabric-api-0.141.6+1.21.11.jar";
                using var resp = await _httpClient.GetAsync(downloadUrl, cancellationToken);
                if (resp.IsSuccessStatusCode)
                {
                    await using var fs = File.Create(target);
                    await resp.Content.CopyToAsync(fs, cancellationToken);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[MinecraftLauncherService] Fabric API setup error: {ex.Message}");
            }
        }

        private async Task DownloadFabricLibrariesAsync(string minecraftPath, string versionJsonContent, CancellationToken cancellationToken)
        {
            try
            {
                using var doc = JsonDocument.Parse(versionJsonContent);
                if (doc.RootElement.TryGetProperty("libraries", out var libraries))
                {
                    foreach (var lib in libraries.EnumerateArray())
                    {
                        if (lib.TryGetProperty("name", out var nameElem))
                        {
                            string mavenName = nameElem.GetString() ?? "";
                            string baseUrl = lib.TryGetProperty("url", out var urlElem) ? urlElem.GetString() ?? "https://maven.fabricmc.net/" : "https://maven.fabricmc.net/";
                            if (string.IsNullOrWhiteSpace(baseUrl)) baseUrl = "https://maven.fabricmc.net/";
                            if (!baseUrl.EndsWith("/")) baseUrl += "/";

                            // Парсим org.example:artifact:version
                            var parts = mavenName.Split(':');
                            if (parts.Length >= 3)
                            {
                                string groupPath = parts[0].Replace('.', '/');
                                string artifact = parts[1];
                                string version = parts[2];
                                string jarFileName = $"{artifact}-{version}.jar";
                                string relativePath = Path.Combine(groupPath.Replace('/', Path.DirectorySeparatorChar), artifact, version, jarFileName);
                                string localFilePath = Path.Combine(minecraftPath, "libraries", relativePath);

                                if (!File.Exists(localFilePath))
                                {
                                    string? parentDir = Path.GetDirectoryName(localFilePath);
                                    if (parentDir != null)
                                    {
                                        Directory.CreateDirectory(parentDir);
                                    }
                                    string downloadUrl = $"{baseUrl}{groupPath}/{artifact}/{version}/{jarFileName}";
                                    try
                                    {
                                        using var libResp = await _httpClient.GetAsync(downloadUrl, cancellationToken);
                                        if (libResp.IsSuccessStatusCode)
                                        {
                                            await using var fs = File.Create(localFilePath);
                                            await libResp.Content.CopyToAsync(fs, cancellationToken);
                                        }
                                    }
                                    catch { }
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[MinecraftLauncherService] Library download error: {ex.Message}");
            }
        }

        public void RegisterLauncherProfile(LauncherConfig config, string mcVersion = "1.21.11", string loaderVersion = "0.19.5")
        {
            try
            {
                string profilesFile = Path.Combine(config.MinecraftPath, "launcher_profiles.json");
                JsonObject? root = null;

                if (File.Exists(profilesFile))
                {
                    string json = File.ReadAllText(profilesFile);
                    var node = JsonNode.Parse(json);
                    if (node is JsonObject jo)
                    {
                        root = jo;
                    }
                }

                root ??= new JsonObject();

                if (!root.ContainsKey("profiles") || root["profiles"] is not JsonObject profiles)
                {
                    profiles = new JsonObject();
                    root["profiles"] = profiles;
                }

                string versionId = $"fabric-loader-{loaderVersion}-{mcVersion}";
                var mouseProfile = new JsonObject
                {
                    ["created"] = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ"),
                    ["icon"] = "Crafting_Table",
                    ["lastUsed"] = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ"),
                    ["lastVersionId"] = versionId,
                    ["name"] = "MouseClient",
                    ["type"] = "custom",
                    ["javaArgs"] = $"-Xmx{config.RamMb}M {config.CustomJvmArgs}"
                };

                profiles["MouseClient"] = mouseProfile;

                File.WriteAllText(profilesFile, root.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[MinecraftLauncherService] Register profile error: {ex.Message}");
            }
        }

        public async Task<LaunchResult> LaunchDirectAsync(
            LauncherConfig config,
            ClientVersionInfo versionInfo,
            IProgress<string>? statusProgress = null,
            CancellationToken cancellationToken = default)
        {
            var result = new LaunchResult();

            // 1. Проверяем Java 21+
            statusProgress?.Report("Проверка среды Java...");
            var java = _javaDetector.DetectJava(config.JavaPath);
            if (!java.IsValid)
            {
                result.Success = false;
                result.Message = "Не найдена Java 21 или новее. Установите JDK 21 (например, Amazon Corretto или Adoptium).";
                return result;
            }

            // 2. Убеждаемся, что Fabric установлен и профиль создан
            statusProgress?.Report("Подготовка файлов Fabric...");
            await EnsureFabricInstalledAsync(config.MinecraftPath, versionInfo.MinecraftVersion, versionInfo.FabricVersion, statusProgress, cancellationToken);
            await EnsureFabricApiAsync(config.MinecraftPath, statusProgress, cancellationToken);
            RegisterLauncherProfile(config, versionInfo.MinecraftVersion, versionInfo.FabricVersion);

            // 3. Формируем пути и аргументы запуска
            string versionId = $"fabric-loader-{versionInfo.FabricVersion}-{versionInfo.MinecraftVersion}";
            string versionJsonPath = Path.Combine(config.MinecraftPath, "versions", versionId, $"{versionId}.json");
            string mcJarPath = Path.Combine(config.MinecraftPath, "versions", versionInfo.MinecraftVersion, $"{versionInfo.MinecraftVersion}.jar");

            if (!File.Exists(versionJsonPath))
            {
                result.Success = false;
                result.Message = $"Файл версии {versionId}.json не найден в .minecraft/versions.";
                return result;
            }

            statusProgress?.Report("Сборка библиотек и Classpath...");
            var classpaths = new List<string>();

            // Читаем библиотеки из Fabric JSON
            string jsonContent = await File.ReadAllTextAsync(versionJsonPath, cancellationToken);
            using var doc = JsonDocument.Parse(jsonContent);

            if (doc.RootElement.TryGetProperty("libraries", out var libraries))
            {
                foreach (var lib in libraries.EnumerateArray())
                {
                    if (lib.TryGetProperty("name", out var nameElem))
                    {
                        string mavenName = nameElem.GetString() ?? "";
                        var parts = mavenName.Split(':');
                        if (parts.Length >= 3)
                        {
                            string groupPath = parts[0].Replace('.', Path.DirectorySeparatorChar);
                            string artifact = parts[1];
                            string version = parts[2];
                            string jarFileName = $"{artifact}-{version}.jar";
                            string localFilePath = Path.Combine(config.MinecraftPath, "libraries", groupPath, artifact, version, jarFileName);
                            if (File.Exists(localFilePath))
                            {
                                classpaths.Add(localFilePath);
                            }
                        }
                    }
                }
            }

            // Читаем библиотеки из оригинальной версии Minecraft (1.21.11.json)
            string baseJsonPath = Path.Combine(config.MinecraftPath, "versions", versionInfo.MinecraftVersion, $"{versionInfo.MinecraftVersion}.json");
            if (File.Exists(baseJsonPath))
            {
                try
                {
                    string baseJson = await File.ReadAllTextAsync(baseJsonPath, cancellationToken);
                    using var baseDoc = JsonDocument.Parse(baseJson);
                    if (baseDoc.RootElement.TryGetProperty("libraries", out var baseLibs))
                    {
                        foreach (var lib in baseLibs.EnumerateArray())
                        {
                            if (lib.TryGetProperty("downloads", out var dl) &&
                                dl.TryGetProperty("artifact", out var art) &&
                                art.TryGetProperty("path", out var pathElem))
                            {
                                string rel = pathElem.GetString() ?? "";
                                string full = Path.Combine(config.MinecraftPath, "libraries", rel.Replace('/', Path.DirectorySeparatorChar));
                                if (File.Exists(full) && !classpaths.Contains(full))
                                {
                                    classpaths.Add(full);
                                }
                            }
                        }
                    }
                }
                catch { }
            }

            // Добавляем саму версию Minecraft 1.21.11.jar в classpath
            if (File.Exists(mcJarPath))
            {
                classpaths.Add(mcJarPath);
            }

            // Ищем все jar из libraries если classpath маловат
            if (classpaths.Count < 5)
            {
                string libRoot = Path.Combine(config.MinecraftPath, "libraries");
                if (Directory.Exists(libRoot))
                {
                    var extraJars = Directory.GetFiles(libRoot, "*.jar", SearchOption.AllDirectories);
                    foreach (var j in extraJars)
                    {
                        if (!classpaths.Contains(j)) classpaths.Add(j);
                    }
                }
            }

            string combinedCp = string.Join(";", classpaths);
            string assetsDir = Path.Combine(config.MinecraftPath, "assets");
            string uuid = Guid.NewGuid().ToString("N");
            string username = string.IsNullOrWhiteSpace(config.Nickname) ? "Player" : config.Nickname.Trim();

            // Аргументы JVM
            string jvmArgs = $"-Xmx{config.RamMb}M {config.CustomJvmArgs} -DFabricMcEmu=\"net.minecraft.client.main.Main\" -cp \"{combinedCp}\" net.fabricmc.loader.impl.launch.knot.KnotClient --username {username} --version {versionInfo.MinecraftVersion} --gameDir \"{config.MinecraftPath}\" --assetsDir \"{assetsDir}\" --assetIndex {versionInfo.MinecraftVersion} --uuid {uuid} --accessToken 0 --userType mojang";

            statusProgress?.Report("Запуск процесса Minecraft...");

            var startInfo = new ProcessStartInfo
            {
                FileName = java.Path,
                Arguments = jvmArgs,
                WorkingDirectory = config.MinecraftPath,
                UseShellExecute = false,
                CreateNoWindow = false
            };

            try
            {
                var process = Process.Start(startInfo);
                if (process != null)
                {
                    result.Success = true;
                    result.Message = "Игра успешно запущена!";
                    result.GameProcess = process;
                }
                else
                {
                    result.Success = false;
                    result.Message = "Не удалось запустить процесс игры.";
                }
            }
            catch (Exception ex)
            {
                result.Success = false;
                result.Message = $"Ошибка при запуске: {ex.Message}";
            }

            return result;
        }
    }
}
