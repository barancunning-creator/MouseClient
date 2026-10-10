using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace MouseLauncher
{
    public partial class App : Application
    {
        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);

            AppDomain.CurrentDomain.UnhandledException += (s, args) =>
            {
                LogCrash(args.ExceptionObject as Exception);
            };

            DispatcherUnhandledException += (s, args) =>
            {
                LogCrash(args.Exception);
                args.Handled = true;
            };
        }

        private static void LogCrash(Exception? ex)
        {
            if (ex == null) return;
            try
            {
                string appData = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                    "MouseLauncher");
                Directory.CreateDirectory(appData);
                string logFile = Path.Combine(appData, "crash.log");
                File.AppendAllText(logFile, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}\n\n");
                MessageBox.Show(
                    $"Произошла ошибка в лаунчере:\n{ex.Message}\n\nПодробный лог сохранен в:\n{logFile}",
                    "MouseClient Launcher",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
            }
            catch { }
        }
    }
}
