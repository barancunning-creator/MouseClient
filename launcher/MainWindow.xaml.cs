using System;
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using MouseLauncher.Models;
using MouseLauncher.Services;

namespace MouseLauncher
{
    public partial class MainWindow : Window
    {
        private readonly ConfigService _configService = new();
        private readonly UpdateService _updateService = new();
        private readonly MinecraftLauncherService _launcherService = new();
        private readonly JavaDetector _javaDetector = new();

        private LauncherConfig _config = new();
        private ClientVersionInfo _versionInfo = new();
        private JavaInfo _javaInfo = new();
        private bool _isBusy = false;
        private bool _isInitialized = false;

        private Storyboard? _spinClockwise;
        private Storyboard? _spinCounterClockwise;
        private Storyboard? _pulseLogo;

        public MainWindow()
        {
            InitializeComponent();
        }

        private async void Window_Loaded(object sender, RoutedEventArgs e)
        {
            try
            {
                // 1. Загружаем сохраненную конфигурацию
                _config = _configService.Load();

                TxtNickname.Text = _config.Nickname;
                SliderRam.Value = _config.RamMb;
                UpdateRamDisplay(_config.RamMb);
                ChkAutoClose.IsChecked = _config.AutoClose;

                // 2. Детектим Java 21+
                CheckJavaEnvironment();

                _isInitialized = true;

                // 3. Проверяем обновления на GitHub
                await CheckUpdatesAsync();
            }
            catch (Exception ex)
            {
                TxtStatus.Text = $"Ошибка загрузки: {ex.Message}";
                _isInitialized = true;
            }
        }

        private void CheckJavaEnvironment()
        {
            try
            {
                _javaInfo = _javaDetector.DetectJava(_config.JavaPath);
                if (_javaInfo.IsValid)
                {
                    ElpJavaDot.Fill = new SolidColorBrush(Color.FromRgb(0, 229, 255)); // Неоновый Cyan
                    string shortName = Path.GetFileName(Path.GetDirectoryName(Path.GetDirectoryName(_javaInfo.Path) ?? "") ?? "") ?? "Java 21";
                    TxtJavaStatus.Text = $"Java {_javaInfo.MajorVersion} ({shortName}) готова";
                }
                else
                {
                    ElpJavaDot.Fill = new SolidColorBrush(Color.FromRgb(239, 68, 68)); // Красный
                    TxtJavaStatus.Text = "Java 21 не найдена! Установите JDK 21+";
                }
            }
            catch (Exception ex)
            {
                TxtJavaStatus.Text = $"Ошибка проверки Java: {ex.Message}";
            }
        }

        private async Task CheckUpdatesAsync()
        {
            TxtStatus.Text = "Синхронизация с сервером...";
            try
            {
                _versionInfo = await _updateService.CheckForUpdatesAsync();

                TxtTitleVersion.Text = $"v{_versionInfo.Version}";
                ItemsChangelog.ItemsSource = _versionInfo.Changelog;

                bool isInstalled = _updateService.IsClientInstalled(_config.MinecraftPath, _versionInfo);
                if (isInstalled)
                {
                    TxtStatus.Text = $"MouseClient v{_versionInfo.Version} готов к игре";
                }
                else
                {
                    TxtStatus.Text = $"Доступно обновление v{_versionInfo.Version}";
                }
            }
            catch (Exception ex)
            {
                TxtStatus.Text = $"Оффлайн-режим: {ex.Message}";
            }
        }

        private void UpdateRamDisplay(int ramMb)
        {
            double gb = ramMb / 1024.0;
            TxtRamValue.Text = $"{ramMb} МБ ({gb:F1} ГБ)";
        }

        private void SliderRam_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (!_isInitialized || _config == null || _configService == null) return;
            int ram = (int)e.NewValue;
            ram = (ram / 512) * 512;
            _config.RamMb = ram;
            UpdateRamDisplay(ram);
            _configService.Save(_config);
        }

        private void TxtNickname_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e)
        {
            if (!_isInitialized || _config == null || _configService == null || TxtNickname == null) return;
            _config.Nickname = TxtNickname.Text.Trim();
            _configService.Save(_config);
        }

        private void ChkAutoClose_Changed(object sender, RoutedEventArgs e)
        {
            if (!_isInitialized || _config == null || _configService == null || ChkAutoClose == null) return;
            _config.AutoClose = ChkAutoClose.IsChecked == true;
            _configService.Save(_config);
        }

        private void TitleBar_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                DragMove();
            }
        }

        private void BtnMinimize_Click(object sender, RoutedEventArgs e)
        {
            WindowState = WindowState.Minimized;
        }

        private void BtnClose_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void BtnOpenMods_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string mods = Path.Combine(_config.MinecraftPath, "mods");
                if (!Directory.Exists(mods))
                {
                    Directory.CreateDirectory(mods);
                }
                Process.Start(new ProcessStartInfo("explorer.exe", mods) { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Не удалось открыть папку mods: {ex.Message}", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnOpenGithub_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string url = "https://github.com/barancunning-creator/MouseClient";
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch { }
        }

        private async void BtnPlay_Click(object sender, RoutedEventArgs e)
        {
            if (_isBusy) return;
            await LaunchGameFlowAsync();
        }

        private void StartLoadingAnimation()
        {
            LoadingOverlay.Visibility = Visibility.Visible;
            var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220));
            LoadingOverlay.BeginAnimation(UIElement.OpacityProperty, fadeIn);

            _spinClockwise ??= (Storyboard)Resources["SpinClockwise"];
            _spinCounterClockwise ??= (Storyboard)Resources["SpinCounterClockwise"];
            _pulseLogo ??= (Storyboard)Resources["PulseLogo"];

            _spinClockwise.Begin(this, true);
            _spinCounterClockwise.Begin(this, true);
            _pulseLogo.Begin(this, true);
        }

        private async Task StopLoadingAnimationAsync()
        {
            var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(300));
            LoadingOverlay.BeginAnimation(UIElement.OpacityProperty, fadeOut);
            await Task.Delay(300);
            LoadingOverlay.Visibility = Visibility.Collapsed;

            _spinClockwise?.Stop(this);
            _spinCounterClockwise?.Stop(this);
            _pulseLogo?.Stop(this);
        }

        private void SetLoadingProgress(double percent, string status)
        {
            PbLoading.Value = Math.Clamp(percent, 0, 100);
            TxtLoadingStatus.Text = status;
            TxtLoadingPercent.Text = $"{percent:F0}%";
        }

        private async Task LaunchGameFlowAsync()
        {
            _isBusy = true;
            BtnPlay.IsEnabled = false;

            StartLoadingAnimation();
            SetLoadingProgress(10, "Проверка файлов клиента...");

            try
            {
                if (_versionInfo == null)
                {
                    _versionInfo = await _updateService.CheckForUpdatesAsync();
                }

                // 1. Проверяем наличие мода в mods/
                SetLoadingProgress(25, "Синхронизация MouseClient...");
                bool isInstalled = _updateService.IsClientInstalled(_config.MinecraftPath, _versionInfo);
                if (!isInstalled)
                {
                    var progress = new Progress<UpdateProgressReport>(p =>
                    {
                        double mapped = 25 + (p.Percentage * 0.25);
                        SetLoadingProgress(mapped, p.StatusMessage);
                    });

                    await _updateService.DownloadAndInstallClientAsync(_config.MinecraftPath, _versionInfo, progress);
                    _config.LastInstalledVersion = _versionInfo.Version;
                    _configService.Save(_config);
                }

                // 2. Fabric Loader & Fabric API
                SetLoadingProgress(55, "Проверка Fabric Loader...");
                await _launcherService.EnsureFabricInstalledAsync(
                    _config.MinecraftPath,
                    _versionInfo.MinecraftVersion,
                    _versionInfo.FabricVersion,
                    new Progress<string>(msg => SetLoadingProgress(60, msg)));

                SetLoadingProgress(70, "Проверка Fabric API...");
                await _launcherService.EnsureFabricApiAsync(
                    _config.MinecraftPath,
                    new Progress<string>(msg => SetLoadingProgress(75, msg)));

                // 3. Базовые библиотеки Mojang
                SetLoadingProgress(80, "Подготовка библиотек Minecraft...");
                await _launcherService.EnsureBaseMinecraftLibrariesAsync(
                    _config.MinecraftPath,
                    _versionInfo.MinecraftVersion,
                    new Progress<string>(msg => SetLoadingProgress(85, msg)));

                _launcherService.RegisterLauncherProfile(_config, _versionInfo.MinecraftVersion, _versionInfo.FabricVersion);

                // 4. Запуск игры
                SetLoadingProgress(95, "Запуск процесса Minecraft...");
                var launchResult = await _launcherService.LaunchDirectAsync(
                    _config,
                    _versionInfo,
                    new Progress<string>(msg => SetLoadingProgress(98, msg)));

                if (launchResult.Success)
                {
                    SetLoadingProgress(100, "Игра успешно запущена!");
                    TxtStatus.Text = "MouseClient запущен. Приятной игры!";

                    if (_config.AutoClose)
                    {
                        await Task.Delay(2200);
                        Close();
                    }
                    else
                    {
                        await Task.Delay(1800);
                        await StopLoadingAnimationAsync();
                    }
                }
                else
                {
                    await StopLoadingAnimationAsync();
                    TxtStatus.Text = "Готово к запуску через лаунчер";
                    MessageBox.Show(
                        $"{launchResult.Message}\n\nМод уже успешно установлен в mods/.\nВы можете запустить профиль 'MouseClient' через любой установленный лаунчер Minecraft (TLauncher, Official, Prism).",
                        "MouseClient",
                        MessageBoxButton.OK,
                        MessageBoxImage.Information);
                }
            }
            catch (Exception ex)
            {
                await StopLoadingAnimationAsync();
                TxtStatus.Text = $"Ошибка: {ex.Message}";
                MessageBox.Show($"Произошла ошибка: {ex.Message}", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
            finally
            {
                _isBusy = false;
                BtnPlay.IsEnabled = true;
            }
        }
    }
}