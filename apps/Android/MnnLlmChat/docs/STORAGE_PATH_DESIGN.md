# MNN 模型存储路径自定义设计

## 目标

将 MnnLlmChat 的模型文件存储位置从内部存储（`filesDir/.mnnmodels`）改为可自定义的外部路径，解决大型语言模型（GB 级）占满内部存储的问题。

## 架构现状

```
ModelDownloadManager (单例)
│  ├─ cacheDir = context.filesDir + "/.mnnmodels"   ← 硬编码
│  ├─ HfModelDownloader(cacheDir)
│  └─ MsModelDownloader(cacheDir)
│
├─ ModelMarketViewModel
│     └─ getDownloadInfo() / getDownloadedFile() → 查询模型路径
│
├─ VoiceModelPathUtils
│     └─ getDownloadedFile() → 获取 ASR/TTS 模型路径
│
└─ StorageManagementActivity  ← 已存在，管理模型存储空间
```

## 修改方案

### 1. 权限 (参考 streamclip)
- Android 11+: `MANAGE_EXTERNAL_STORAGE`，跳系统设置授权
- 启动时检查，未授权弹窗引导

### 2. 存储路径偏好 (MainSettings)
```kotlin
fun getModelStoragePath(context: Context): String
fun setModelStoragePath(context: Context, path: String)
```
默认值返回 `context.filesDir/.mnnmodels`（保持兼容）

### 3. ModelDownloadManager 动态路径
`cacheDir` 从 `val` 改为 `get()`，实时读取 MainSettings 偏好

### 4. UI
在 `fragment_main_settings.xml` 设置页已有 `StorageManagement` 入口，旁边新增"模型存储位置"行：
- 显示当前路径
- 点击弹出目录选择
- 选择后重建 ModelDownloadManager 单例

### 5. 自动刷新
`ModelMarketViewModel` 通过 `ModelDownloadManager.getInstance()` 获取单例，路径变更后重建单例，下次进入模型市场 UI 自动读取新路径下的模型。

## 影响的文件

| 文件 | 改动类型 |
|------|----------|
| `AndroidManifest.xml` | 加权限 |
| `ChatActivity.kt` | 加运行时权限请求 |
| `MainSettings.kt` | 新增路径 getter/setter |
| `ModelDownloadManager.kt` | cacheDir 动态化 |
| `MainSettingsFragment.kt` | 新增路径选择 UI |
| `fragment_main_settings.xml` | 新增行布局 |
