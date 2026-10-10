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
            // 1. Запуск красивых анимаций колец и логотипа
            StartAnimations();

            try
            {
                // Загружаем конфиг
                _config = _configService.Load();

                TxtNickname.Text = _config.Nickname;
                TxtHomeNickname.Text = _config.Nickname;
                SliderRam.Value = _config.RamMb;
                UpdateRamDisplay(_config.RamMb);
                TxtGamePath.Text = _config.MinecraftPath;
                ChkAutoClose.IsChecked = _config.AutoClose;

                PbSplash.Value = 40;
                TxtSplashStatus.Text = "Проверка среды Java...";
                CheckJavaEnvironment();

                _isInitialized = true;

                PbSplash.Value = 75;
                TxtSplashStatus.Text = "Синхронизация с GitHub...";
                await CheckUpdatesAsync();

                PbSplash.Value = 100;
                TxtSplashStatus.Text = "Готово!";
                await Task.Delay(500);

                // Плавное скрытие стартовой заставки
                var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(400));
                StartupSplashOverlay.BeginAnimation(UIElement.OpacityProperty, fadeOut);
                await Task.Delay(400);
                StartupSplashOverlay.Visibility = Visibility.Collapsed;
            }
            catch (Exception ex)
            {
                StartupSplashOverlay.Visibility = Visibility.Collapsed;
                TxtHomeStatus.Text = $"Ошибка: {ex.Message}";
                _isInitialized = true;
            }
        }

        private void StartAnimations()
        {
            try
            {
                _spinClockwise = (Storyboard)Resources["SpinClockwise"];
                _spinCounterClockwise = (Storyboard)Resources["SpinCounterClockwise"];
                _pulseLogo = (Storyboard)Resources["PulseLogo"];

                _spinClockwise.Begin(this, true);
                _spinCounterClockwise.Begin(this, true);
                _pulseLogo.Begin(this, true);
            }
            catch { }
        }

        private void Tab_Checked(object sender, RoutedEventArgs e)
        {
            if (!_isInitialized || PanelHome == null) return;

            FrameworkElement? target = null;
            if (TabHome.IsChecked == true) target = PanelHome;
            else if (TabSettings.IsChecked == true) target = PanelSettings;
            else if (TabChangelog.IsChecked == true) target = PanelChangelog;
            else if (TabAbout.IsChecked == true) target = PanelAbout;

            if (target != null)
            {
                SwitchToTab(target);
            }
        }

        private void SwitchToTab(FrameworkElement targetPanel)
        {
            var panels = new FrameworkElement[] { PanelHome, PanelSettings, PanelChangelog, PanelAbout };
            foreach (var p in panels)
            {
                if (p == targetPanel)
                {
                    p.Visibility = Visibility.Visible;

                    // Плавная анимация проявления (Fade-in)
                    var fadeIn = new DoubleAnimation
                    {
                        From = 0.0,
                        To = 1.0,
                        Duration = TimeSpan.FromMilliseconds(220),
                        EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                    };

                    // Плавный подъем со смещением по оси Y на 14 пикселей (Slide-up)
                    var translate = new TranslateTransform(0, 14);
                    p.RenderTransform = translate;

                    var slideUp = new DoubleAnimation
                    {
                        From = 14.0,
                        To = 0.0,
                        Duration = TimeSpan.FromMilliseconds(220),
                        EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                    };

                    p.BeginAnimation(UIElement.OpacityProperty, fadeIn);
                    translate.BeginAnimation(TranslateTransform.YProperty, slideUp);
                }
                else
                {
                    p.Visibility = Visibility.Collapsed;
                }
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
            try
            {
                _versionInfo = await _updateService.CheckForUpdatesAsync();

                TxtTitleVersion.Text = $"v{_versionInfo.Version} Релиз";
                ItemsChangelog.ItemsSource = _versionInfo.Changelog;

                bool isInstalled = _updateService.IsClientInstalled(_config.MinecraftPath, _versionInfo);
                if (isInstalled)
                {
                    TxtHomeStatus.Text = $"Релиз v{_versionInfo.Version} готов";
                }
                else
                {
                    TxtHomeStatus.Text = $"Релиз v{_versionInfo.Version}";
                }
            }
            catch
            {
                TxtHomeStatus.Text = "Готов к игре";
            }
        }

        private void UpdateRamDisplay(int ramMb)
        {
            double gb = ramMb / 1024.0;
            TxtRamValue.Text = $"{ramMb} МБ ({gb:F1} ГБ)";
            if (TxtHomeRam != null)
            {
                TxtHomeRam.Text = $"{ramMb} МБ";
            }
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
            if (TxtHomeNickname != null)
            {
                TxtHomeNickname.Text = string.IsNullOrWhiteSpace(_config.Nickname) ? "Player" : _config.Nickname;
            }
            _configService.Save(_config);
        }

        private void ChkAutoClose_Changed(object sender, RoutedEventArgs e)
        {
            if (!_isInitialized || _config == null || _configService == null || ChkAutoClose == null) return;
            _config.AutoClose = ChkAutoClose.IsChecked == true;
            _configService.Save(_config);
        }

        private void BtnBrowsePath_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new Microsoft.Win32.OpenFolderDialog
            {
                Title = "Выберите папку .minecraft",
                InitialDirectory = _config.MinecraftPath
            };

            if (dialog.ShowDialog() == true)
            {
                _config.MinecraftPath = dialog.FolderName;
                TxtGamePath.Text = dialog.FolderName;
                _configService.Save(_config);
                _ = CheckUpdatesAsync();
            }
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

        private void BtnOpenMinecraft_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                if (!Directory.Exists(_config.MinecraftPath))
                {
                    Directory.CreateDirectory(_config.MinecraftPath);
                }
                Process.Start(new ProcessStartInfo("explorer.exe", _config.MinecraftPath) { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Не удалось открыть папку: {ex.Message}", "Ошибка", MessageBoxButton.OK, MessageBoxImage.Error);
            }
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

        private void ShowGameLaunchOverlay()
        {
            GameLaunchOverlay.Visibility = Visibility.Visible;
            var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220));
            GameLaunchOverlay.BeginAnimation(UIElement.OpacityProperty, fadeIn);
        }

        private async Task HideGameLaunchOverlayAsync()
        {
            var fadeOut = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(300));
            GameLaunchOverlay.BeginAnimation(UIElement.OpacityProperty, fadeOut);
            await Task.Delay(300);
            GameLaunchOverlay.Visibility = Visibility.Collapsed;
        }

        private void SetLaunchProgress(double percent, string status)
        {
            PbLaunch.Value = Math.Clamp(percent, 0, 100);
            TxtLaunchStatus.Text = status;
            TxtLaunchPercent.Text = $"{percent:F0}%";
        }

        private async Task LaunchGameFlowAsync()
        {
            _isBusy = true;
            BtnPlay.IsEnabled = false;

            ShowGameLaunchOverlay();
            SetLaunchProgress(10, "Проверка клиента...");

            try
            {
                if (_versionInfo == null)
                {
                    _versionInfo = await _updateService.CheckForUpdatesAsync();
                }

                // 1. Проверяем наличие мода в mods/
                SetLaunchProgress(25, "Синхронизация MouseClient...");
                bool isInstalled = _updateService.IsClientInstalled(_config.MinecraftPath, _versionInfo);
                if (!isInstalled)
                {
                    var progress = new Progress<UpdateProgressReport>(p =>
                    {
                        double mapped = 25 + (p.Percentage * 0.25);
                        SetLaunchProgress(mapped, p.StatusMessage);
                    });

                    await _updateService.DownloadAndInstallClientAsync(_config.MinecraftPath, _versionInfo, progress);
                    _config.LastInstalledVersion = _versionInfo.Version;
                    _configService.Save(_config);
                }

                // 2. Fabric Loader & Fabric API
                SetLaunchProgress(55, "Проверка Fabric Loader...");
                await _launcherService.EnsureFabricInstalledAsync(
                    _config.MinecraftPath,
                    _versionInfo.MinecraftVersion,
                    _versionInfo.FabricVersion,
                    new Progress<string>(msg => SetLaunchProgress(60, msg)));

                SetLaunchProgress(70, "Проверка Fabric API...");
                await _launcherService.EnsureFabricApiAsync(
                    _config.MinecraftPath,
                    new Progress<string>(msg => SetLaunchProgress(75, msg)));

                // 3. Базовые библиотеки
                SetLaunchProgress(80, "Подготовка библиотек Minecraft...");
                await _launcherService.EnsureBaseMinecraftLibrariesAsync(
                    _config.MinecraftPath,
                    _versionInfo.MinecraftVersion,
                    new Progress<string>(msg => SetLaunchProgress(85, msg)));

                _launcherService.RegisterLauncherProfile(_config, _versionInfo.MinecraftVersion, _versionInfo.FabricVersion);

                // 4. Запуск процесса
                SetLaunchProgress(95, "Запуск процесса Minecraft...");
                var launchResult = await _launcherService.LaunchDirectAsync(
                    _config,
                    _versionInfo,
                    new Progress<string>(msg => SetLaunchProgress(98, msg)));

                if (launchResult.Success)
                {
                    SetLaunchProgress(100, "Игра успешно запущена!");
                    TxtHomeStatus.Text = "Игра запущена!";

                    if (_config.AutoClose)
                    {
                        await Task.Delay(2200);
                        Close();
                    }
                    else
                    {
                        await Task.Delay(1800);
                        await HideGameLaunchOverlayAsync();
                    }
                }
                else
                {
                    await HideGameLaunchOverlayAsync();
                    TxtHomeStatus.Text = "Готово к запуску";
                    MessageBox.Show(
                        $"{launchResult.Message}\n\nМод уже успешно установлен в mods/.\nВы можете запустить профиль 'MouseClient' через любой установленный лаунчер Minecraft (TLauncher, Official, Prism).",
                        "MouseClient",
                        MessageBoxButton.OK,
                        MessageBoxImage.Information);
                }
            }
            catch (Exception ex)
            {
                await HideGameLaunchOverlayAsync();
                TxtHomeStatus.Text = $"Ошибка: {ex.Message}";
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