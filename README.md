# Nextcloud Talk for Wear OS

📱 WearOS 客户端 for Nextcloud Talk

## 功能

- 会话列表与消息查看
- 快速回复与文字输入
- 语音消息录制发送
- 消息推送通知
- Bot 管理
- 会话管理（创建、置顶、删除）

## 作者

**Mubai1124**

## 开源协议

GPL-3.0-or-later

本项目基于 [Nextcloud Talk Android](https://github.com/nextcloud/talk-android) 开发。

## 第三方库

| 库名 | 协议 |
|------|------|
| OkHttp | Apache 2.0 |
| Retrofit | Apache 2.0 |
| Gson | Apache 2.0 |
| Jetpack Compose | Apache 2.0 |
| Room | Apache 2.0 |
| Kotlin Coroutines | Apache 2.0 |

## 构建

```bash
# Debug 版本
./gradlew :wear:assembleDebug

# Release 版本（需要签名密钥）
./gradlew :wear:assembleRelease
```

## 系统要求

- Wear OS 3.0+ (API 30+)
- Nextcloud Talk 服务器

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

感谢 Nextcloud 团队提供的开源 Talk 应用。

---

**注意**: 本项目与 Nextcloud 官方无关，为第三方实现。
