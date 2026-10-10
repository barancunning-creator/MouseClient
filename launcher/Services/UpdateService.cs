using System;
using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using MouseLauncher.Models;

namespace MouseLauncher.Services
{
    public class UpdateProgressReport
    {
        public double Percentage { get; set; }
        public long BytesReceived { get; set; }
        public long TotalBytes { get; set; }
        public double SpeedKiloBytesPerSec { get; set; }
        public string StatusMessage { get; set; } = "";
    }

    public class UpdateService
    {
        private const string DefaultVersionJsonUrl =
            "https://raw.githubusercontent.com/barancunning-creator/MouseClient/main/version.json";

        private const string GithubLatestReleaseApi =
            "https://api.github.com/repos/barancunning-creator/MouseClient/releases/latest";

        private readonly HttpClient _httpClient;

        public UpdateService()
        {
            _httpClient = new HttpClient();
            _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("MouseClient-Launcher/1.0.0");
            _httpClient.Timeout = TimeSpan.FromSeconds(30);
        }

        public async Task<ClientVersionInfo> CheckForUpdatesAsync(CancellationToken cancellationToken = default)
        {
            ClientVersionInfo? info = null;

            // 1. Попытка получить version.json с сырого GitHub
            try
            {
                using var response = await _httpClient.GetAsync(DefaultVersionJsonUrl, cancellationToken);
                if (response.IsSuccessStatusCode)
                {
                    string json = await response.Content.ReadAsStringAsync(cancellationToken);
                    info = JsonSerializer.Deserialize<ClientVersionInfo>(json, new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    });
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[UpdateService] Error fetching version.json: {ex.Message}");
            }

            // 2. Если version.json не удалось получить, или в нем нет прямой ссылки, опрашиваем GitHub Releases API
            if (info == null || string.IsNullOrWhiteSpace(info.DownloadUrl))
            {
                try
                {
                    using var releaseResponse = await _httpClient.GetAsync(GithubLatestReleaseApi, cancellationToken);
                    if (releaseResponse.IsSuccessStatusCode)
                    {
                        string releaseJson = await releaseResponse.Content.ReadAsStringAsync(cancellationToken);
                        using var doc = JsonDocument.Parse(releaseJson);
                        var root = doc.RootElement;

                        string tagName = root.TryGetProperty("tag_name", out var tagElem) ? tagElem.GetString() ?? "1.0.0" : "1.0.0";
                        string cleanVersion = tagName.TrimStart('v', 'V');

                        info ??= new ClientVersionInfo();
                        info.Version = cleanVersion;

                        if (root.TryGetProperty("body", out var bodyElem))
                        {
                            string body = bodyElem.GetString() ?? "";
                            var lines = body.Split(new[] { "\r\n", "\r", "\n" }, StringSplitOptions.RemoveEmptyEntries);
                            info.Changelog.Clear();
                            foreach (var line in lines)
                            {
                                info.Changelog.Add(line.TrimStart('-', '*', ' '));
                            }
                        }

                        if (root.TryGetProperty("assets", out var assetsElem) && assetsElem.GetArrayLength() > 0)
                        {
                            foreach (var asset in assetsElem.EnumerateArray())
                            {
                                string name = asset.GetProperty("name").GetString() ?? "";
                                if (name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase))
                                {
                                    info.JarName = name;
                                    info.DownloadUrl = asset.GetProperty("browser_download_url").GetString() ?? "";
                                    break;
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[UpdateService] Error fetching GitHub Releases API: {ex.Message}");
                }
            }

            // 3. Фолбэк на дефолтную конфигурацию, если GitHub пока пустой или оффлайн
            info ??= new ClientVersionInfo
            {
                Version = "1.0.0",
                MinecraftVersion = "1.21.11",
                FabricVersion = "0.19.5",
                JarName = "MouseClient-1.0.0.jar",
                DownloadUrl = "https://github.com/barancunning-creator/MouseClient/releases/download/v1.0.0/MouseClient-1.0.0.jar",
                Changelog = new()
                {
                    "🚀 Официальный релиз MouseClient v1.0.0 — Fabric 1.21.11",
                    "👑 Самая лучшая модификация для доминирования",
                    "🛡️ Актуальные обходы проверок модерации и античитов",
                    "💎 Полностью бесплатный доступ для всех игроков",
                    "⚡ Максимальный FPS и стабильность работы"
                },
                Notice = "MouseClient 1.0.0 — лучший бесплатный клиент!"
            };

            return info;
        }

        public bool IsClientInstalled(string minecraftPath, ClientVersionInfo info)
        {
            string modsDir = Path.Combine(minecraftPath, "mods");
            if (!Directory.Exists(modsDir)) return false;

            string targetJar = Path.Combine(modsDir, info.JarName);
            if (!File.Exists(targetJar))
            {
                // Проверяем, есть ли хотя бы один файл MouseClient*.jar
                var found = Directory.GetFiles(modsDir, "*MouseClient*.jar");
                if (found.Length == 0)
                {
                    found = Directory.GetFiles(modsDir, "*маусклиент*.jar");
                }
                return found.Length > 0;
            }

            // Если есть sha256 в информации, проверяем хэш
            if (!string.IsNullOrWhiteSpace(info.Sha256))
            {
                string localHash = ComputeSha256(targetJar);
                return string.Equals(localHash, info.Sha256, StringComparison.OrdinalIgnoreCase);
            }

            return true;
        }

        public async Task DownloadAndInstallClientAsync(
            string minecraftPath,
            ClientVersionInfo info,
            IProgress<UpdateProgressReport>? progress,
            CancellationToken cancellationToken = default)
        {
            string modsDir = Path.Combine(minecraftPath, "mods");
            if (!Directory.Exists(modsDir))
            {
                Directory.CreateDirectory(modsDir);
            }

            string targetFile = Path.Combine(modsDir, info.JarName);
            string tempFile = targetFile + ".download.tmp";

            // Локальный скомпилированный билд разработчика
            string localDevBuild = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "..", "..", "..", "..", "build", "libs", "MouseClient-1.0.0.jar");
            if (!File.Exists(localDevBuild))
            {
                localDevBuild = @"d:\MouseClient\build\libs\MouseClient-1.0.0.jar";
            }

            bool downloadedFromNetwork = false;

            // Попытка загрузить из сети
            if (!string.IsNullOrWhiteSpace(info.DownloadUrl) && info.DownloadUrl.StartsWith("http", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    progress?.Report(new UpdateProgressReport
                    {
                        Percentage = 0,
                        StatusMessage = "Подключение к серверу загрузки..."
                    });

                    using var response = await _httpClient.GetAsync(info.DownloadUrl, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
                    if (response.IsSuccessStatusCode)
                    {
                        long totalBytes = response.Content.Headers.ContentLength ?? -1L;

                        await using (var stream = await response.Content.ReadAsStreamAsync(cancellationToken))
                        await using (var fileStream = new FileStream(tempFile, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
                        {
                            byte[] buffer = new byte[81920];
                            long totalRead = 0;
                            int bytesRead;
                            var startTime = DateTime.UtcNow;
                            var lastReportTime = startTime;
                            long bytesSinceLastReport = 0;

                            while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length, cancellationToken)) > 0)
                            {
                                await fileStream.WriteAsync(buffer, 0, bytesRead, cancellationToken);
                                totalRead += bytesRead;
                                bytesSinceLastReport += bytesRead;

                                var now = DateTime.UtcNow;
                                var elapsedSinceLastReport = (now - lastReportTime).TotalSeconds;

                                if (elapsedSinceLastReport >= 0.1 || totalRead == totalBytes)
                                {
                                    double speedKb = (bytesSinceLastReport / 1024.0) / (elapsedSinceLastReport > 0 ? elapsedSinceLastReport : 0.1);
                                    double percentage = totalBytes > 0 ? ((double)totalRead / totalBytes) * 100.0 : 0.0;

                                    progress?.Report(new UpdateProgressReport
                                    {
                                        Percentage = percentage,
                                        BytesReceived = totalRead,
                                        TotalBytes = totalBytes,
                                        SpeedKiloBytesPerSec = speedKb,
                                        StatusMessage = $"Скачивание: {totalRead / 1024 / 1024} МБ / {(totalBytes > 0 ? (totalBytes / 1024 / 1024).ToString() : "?")} МБ ({speedKb:F0} КБ/с)"
                                    });

                                    lastReportTime = now;
                                    bytesSinceLastReport = 0;
                                }
                            }
                        }

                        downloadedFromNetwork = true;
                    }
                    else
                    {
                        System.Diagnostics.Debug.WriteLine($"[UpdateService] Server returned status: {response.StatusCode}");
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[UpdateService] Network download error: {ex.Message}");
                }
            }

            // Если из сети скачать не удалось, но локальный билд найден на диске — устанавливаем его!
            if (!downloadedFromNetwork)
            {
                if (File.Exists(localDevBuild))
                {
                    progress?.Report(new UpdateProgressReport
                    {
                        Percentage = 50,
                        StatusMessage = "Установка из локального билда клиента..."
                    });

                    CleanupOldJars(modsDir, info.JarName);
                    File.Copy(localDevBuild, targetFile, true);

                    progress?.Report(new UpdateProgressReport
                    {
                        Percentage = 100,
                        StatusMessage = "Клиент успешно установлен!"
                    });
                    return;
                }
                else
                {
                    throw new InvalidOperationException(
                        $"Файл клиента не найден по ссылке:\n{info.DownloadUrl}\n(Код 404 Not Found).\n\n" +
                        "Причина: Релиз еще не опубликован на GitHub Releases, либо репозиторий закрыт (Private).\n" +
                        "Сделайте репозиторий публичным (Public) в настройках GitHub или создайте Release 'v1.0.0'.");
                }
            }

            // Очищаем старые версии клиента в папке mods
            progress?.Report(new UpdateProgressReport
            {
                Percentage = 95,
                StatusMessage = "Удаление предыдущих версий клиента..."
            });

            CleanupOldJars(modsDir, info.JarName);

            // Перемещаем скачанный файл
            if (File.Exists(targetFile))
            {
                File.Delete(targetFile);
            }
            File.Move(tempFile, targetFile);

            progress?.Report(new UpdateProgressReport
            {
                Percentage = 100,
                StatusMessage = "Клиент успешно установлен!"
            });
        }

        private void CleanupOldJars(string modsDir, string currentJarName)
        {
            try
            {
                var files = Directory.GetFiles(modsDir, "*MouseClient*.jar");
                foreach (var f in files)
                {
                    if (!string.Equals(Path.GetFileName(f), currentJarName, StringComparison.OrdinalIgnoreCase))
                    {
                        try { File.Delete(f); } catch { }
                    }
                }

                var russianFiles = Directory.GetFiles(modsDir, "*маусклиент*.jar");
                foreach (var f in russianFiles)
                {
                    if (!string.Equals(Path.GetFileName(f), currentJarName, StringComparison.OrdinalIgnoreCase))
                    {
                        try { File.Delete(f); } catch { }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[UpdateService] Cleanup warning: {ex.Message}");
            }
        }

        public static string ComputeSha256(string filePath)
        {
            using var sha = SHA256.Create();
            using var stream = File.OpenRead(filePath);
            byte[] hash = sha.ComputeHash(stream);
            return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        }
    }
}
