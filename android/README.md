# 每日阅读安卓版

适用于 ARM64 手机（Android 7.0 及以上）。安装 APK 后打开「每日阅读」，在「设置」填入你自己的 DeepSeek API Key。首页可以从相册或文件导入百词斩截图，也可以直接输入英文单词。

- 原有的复习和真题生成、历史文章、生词本、拼写复习都在应用内。配置和记录保存在手机的应用数据里，电脑端和手机端各自保存。
- 英文截图 OCR 使用随应用打包的 ML Kit，在手机上完成。生成文章、联网查词以及可选的 AI 拼写校正需要网络和配置好的模型接口。
- 朗读调用安卓系统的文字转语音功能。如果系统没有英文语音，在系统设置里启用或安装英文语音即可。
- 卸载应用或清除应用数据会删除手机上的学习记录。

## 构建 APK

准备 JDK 17 或 21、Gradle 8.10.2、Android SDK 35、NDK 28.0.13004108 和 CMake 3.22.1。将 `ANDROID_HOME` 指向 SDK 目录，然后在仓库根目录运行：

```bash
bash android/fetch-node.sh
gradle -p android :app:assembleDebug
```

安装包位于 `android/app/build/outputs/apk/debug/app-debug.apk`。在 x86_64 模拟器测试时，改用 `bash android/fetch-node.sh x86_64` 和 `gradle -p android :app:assembleDebug -PandroidAbi=x86_64`。

安卓版通过嵌入式 Node.js 复用仓库的本地服务，应用页面通过 WebView 访问手机内部的服务。运行时来自 [lakutata/libnode](https://github.com/lakutata/libnode/releases/tag/v24.20.0)，对应的许可文件随 APK 打包。
