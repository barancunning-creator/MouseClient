using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;

namespace MouseLauncher.Services
{
    public class JavaInfo
    {
        public string Path { get; set; } = "";
        public int MajorVersion { get; set; } = 0;
        public string FullVersionString { get; set; } = "";
        public bool IsValid => !string.IsNullOrEmpty(Path) && File.Exists(Path) && MajorVersion >= 21;
    }

    public class JavaDetector
    {
        public JavaInfo DetectJava(string preferredPath = "")
        {
            if (!string.IsNullOrWhiteSpace(preferredPath) && File.Exists(preferredPath))
            {
                var customInfo = TestJava(preferredPath);
                if (customInfo.IsValid)
                {
                    return customInfo;
                }
            }

            var candidates = new List<string>();

            // 1. JAVA_HOME
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME") ?? "";
            if (!string.IsNullOrWhiteSpace(javaHome))
            {
                candidates.Add(Path.Combine(javaHome, "bin", "javaw.exe"));
                candidates.Add(Path.Combine(javaHome, "bin", "java.exe"));
            }

            // 2. Стандартные папки установки Java на диске C
            string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            string[] searchDirs = new[]
            {
                Path.Combine(programFiles, "Amazon Corretto"),
                Path.Combine(programFiles, "Eclipse Adoptium"),
                Path.Combine(programFiles, "Java"),
                Path.Combine(programFiles, "BellSoft"),
                Path.Combine(programFiles, "Microsoft"),
                Path.Combine(programFiles, "Zulu")
            };

            foreach (var dir in searchDirs)
            {
                if (Directory.Exists(dir))
                {
                    try
                    {
                        var subDirs = Directory.GetDirectories(dir, "*21*");
                        foreach (var sub in subDirs)
                        {
                            candidates.Add(Path.Combine(sub, "bin", "javaw.exe"));
                            candidates.Add(Path.Combine(sub, "bin", "java.exe"));
                        }
                    }
                    catch { }
                }
            }

            // 3. Minecraft Launcher runtimes (java-runtime-gamma / delta)
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

            candidates.Add(Path.Combine(appData, ".minecraft", "runtime", "java-runtime-gamma", "windows-x64", "java-runtime-gamma", "bin", "javaw.exe"));
            candidates.Add(Path.Combine(appData, ".minecraft", "runtime", "java-runtime-delta", "windows-x64", "java-runtime-delta", "bin", "javaw.exe"));

            // 4. Поиск через where.exe в системном PATH
            try
            {
                var whereProc = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = "where.exe",
                        Arguments = "javaw.exe",
                        UseShellExecute = false,
                        RedirectStandardOutput = true,
                        CreateNoWindow = true
                    }
                };
                whereProc.Start();
                string output = whereProc.StandardOutput.ReadToEnd();
                whereProc.WaitForExit(2000);

                var lines = output.Split(new[] { "\r\n", "\r", "\n" }, StringSplitOptions.RemoveEmptyEntries);
                foreach (var line in lines)
                {
                    if (File.Exists(line.Trim()))
                    {
                        candidates.Add(line.Trim());
                    }
                }
            }
            catch { }

            // Проверяем кандидатов по очереди
            foreach (var path in candidates)
            {
                if (File.Exists(path))
                {
                    var info = TestJava(path);
                    if (info.IsValid)
                    {
                        return info;
                    }
                }
            }

            // Если версия < 21 или ничего не найдено, возвращаем лучший найденный или пустой
            return new JavaInfo { Path = "", MajorVersion = 0, FullVersionString = "Не найдена Java 21+" };
        }

        private JavaInfo TestJava(string executablePath)
        {
            var info = new JavaInfo { Path = executablePath };

            try
            {
                // Запускаем java.exe (или заменяем javaw на java для чтения вывода)
                string javaCli = executablePath.EndsWith("javaw.exe", StringComparison.OrdinalIgnoreCase)
                    ? executablePath.Substring(0, executablePath.Length - 9) + "java.exe"
                    : executablePath;

                if (!File.Exists(javaCli))
                {
                    javaCli = executablePath;
                }

                var proc = new Process
                {
                    StartInfo = new ProcessStartInfo
                    {
                        FileName = javaCli,
                        Arguments = "-version",
                        UseShellExecute = false,
                        RedirectStandardError = true,
                        RedirectStandardOutput = true,
                        CreateNoWindow = true
                    }
                };

                proc.Start();
                string errOutput = proc.StandardError.ReadToEnd();
                string stdOutput = proc.StandardOutput.ReadToEnd();
                proc.WaitForExit(3000);

                string full = string.IsNullOrWhiteSpace(errOutput) ? stdOutput : errOutput;
                info.FullVersionString = full;

                // Парсим версию, например 'openjdk version "21.0.11"'
                int quoteIndex = full.IndexOf('"');
                if (quoteIndex >= 0)
                {
                    int nextQuote = full.IndexOf('"', quoteIndex + 1);
                    if (nextQuote > quoteIndex)
                    {
                        string verStr = full.Substring(quoteIndex + 1, nextQuote - quoteIndex - 1);
                        var parts = verStr.Split('.');
                        if (parts.Length > 0 && int.TryParse(parts[0], out int major))
                        {
                            info.MajorVersion = major;
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[JavaDetector] Failed to test {executablePath}: {ex.Message}");
            }

            return info;
        }
    }
}
