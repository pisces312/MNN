$ErrorActionPreference = "Stop"
$NDK = "D:\dev\android_sdk\ndk\27.0.12077973"
$CMake = "D:\dev\android_sdk\cmake\3.22.1\bin\cmake.exe"
$Ninja = "D:\dev\android_sdk\cmake\3.22.1\bin\ninja.exe"
$MNN_ROOT = "D:\3rd-party-projects\MNN"
$BUILD_DIR = "$MNN_ROOT\project\android\build_64"

$env:ANDROID_NDK = $NDK
$env:PATH = "D:\dev\android_sdk\cmake\3.22.1\bin;$env:PATH"

Write-Host "ANDROID_NDK=$env:ANDROID_NDK"
Write-Host "MNN_ROOT=$MNN_ROOT"
Write-Host ""

# Clean build dir
if (Test-Path $BUILD_DIR) {
    Remove-Item -Recurse -Force $BUILD_DIR
}
New-Item -ItemType Directory -Force -Path $BUILD_DIR | Out-Null
Set-Location $BUILD_DIR

Write-Host "=== CMake Configure (Ninja) ==="
$cmakeArgs = @(
    "-G", "Ninja",
    "-DCMAKE_MAKE_PROGRAM=$Ninja",
    "-DCMAKE_TOOLCHAIN_FILE=$NDK\build\cmake\android.toolchain.cmake",
    "-DCMAKE_BUILD_TYPE=Release",
    "-DANDROID_ABI=arm64-v8a",
    "-DANDROID_STL=c++_static",
    "-DANDROID_NATIVE_API_LEVEL=android-21",
    "-DMNN_USE_LOGCAT=true",
    "-DMNN_BUILD_BENCHMARK=OFF",
    "-DMNN_BUILD_TEST=OFF",
    "-DMNN_USE_SSE=OFF",
    "-DMNN_BUILD_FOR_ANDROID_COMMAND=true",
    "-DMNN_LOW_MEMORY=true",
    "-DMNN_CPU_WEIGHT_DEQUANT_GEMM=true",
    "-DMNN_BUILD_LLM=true",
    "-DMNN_SUPPORT_TRANSFORMER_FUSE=true",
    "-DMNN_ARM82=true",
    "-DMNN_OPENCL=true",
    "-DLLM_SUPPORT_VISION=true",
    "-DMNN_BUILD_OPENCV=true",
    "-DMNN_IMGCODECS=true",
    "-DLLM_SUPPORT_AUDIO=true",
    "-DMNN_BUILD_AUDIO=true",
    "-DMNN_BUILD_DIFFUSION=ON",
    "-DMNN_SEP_BUILD=OFF",
    "-DCMAKE_INSTALL_PREFIX=.",
    "../../../"
)

& $CMake @cmakeArgs
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed" }

Write-Host ""
Write-Host "=== Ninja Build ==="
& $Ninja install -j $env:NUMBER_OF_PROCESSORS
if ($LASTEXITCODE -ne 0) { throw "Ninja build failed" }

Write-Host ""
Write-Host "=== Result ==="
if (Test-Path "$BUILD_DIR\lib\libMNN.so") {
    $so = Get-Item "$BUILD_DIR\lib\libMNN.so"
    Write-Host "libMNN.so OK - $([math]::Round($so.Length/1MB, 2)) MB"
} else {
    Write-Host "ERROR: libMNN.so not found!"
}
