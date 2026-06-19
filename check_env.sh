#!/bin/bash
echo "=== WSL NDK Check ==="
echo "ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-<not set>}"
echo "ANDROID_NDK=${ANDROID_NDK:-<not set>}"

# Known paths
for ndk in /home/nili6/android-ndk-r27d /mnt/d/dev/android_sdk/ndk/*; do
  if [ -d "$ndk" ]; then
    echo "Found NDK: $ndk"
    ls "$ndk" | head -3
  fi
done

# Check cmake and make
which cmake && cmake --version | head -1
which make && make --version | head -1

# Check if source directory is accessible
echo "MNN source: /mnt/d/3rd-party-projects/MNN"
ls /mnt/d/3rd-party-projects/MNN/CMakeLists.txt 2>/dev/null && echo "CMakeLists.txt OK" || echo "CMakeLists.txt MISSING"
