@echo off
echo =======================================================
echo          MouseClient Launcher Builder
echo =======================================================
echo.

echo [1/2] Building compact launcher (requires .NET 8)...
dotnet publish MouseLauncher.csproj -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o bin\publish
if errorlevel 1 (
    echo [ERROR] Failed to build compact version.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/2] Building standalone launcher (Zero dependencies, bundled .NET)...
dotnet publish MouseLauncher.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o bin\publish-standalone
if errorlevel 1 (
    echo [ERROR] Failed to build standalone version.
    pause
    exit /b %errorlevel%
)

echo.
echo =======================================================
echo Build complete!
echo Ready files:
echo   - bin\publish\MouseLauncher.exe (~375 KB)
echo   - bin\publish-standalone\MouseLauncher.exe (~71 MB)
echo =======================================================
