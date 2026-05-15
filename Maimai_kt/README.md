# Maimai_kt

这是当前项目的 Kotlin 重构包，结构参考 Python 新包 `maimai_client`。

## 设计分层

- `config`: 配置、环境变量、机厅信息、协议版本。
- `crypto`: TitleServer 请求需要的 MD5、zlib、AES-CBC。
- `transport`: HTTP 请求、cookie、重试、响应解码。
- `api`: `TitleApiClient` 和 `AimeClient`。
- `payload`: `MusicDetail`、playlog、UserAll、patch 合并。
- `service`: 登录、成绩、解锁、票券、版本变更等业务动作。
- `example`: QRCode 登录后的调用示例。

## 基本用法

```kotlin
val actions = MaimaiActions()
val session = actions.sessions.loginByQr(qrCode)

actions.scores.upload(
    userId = session.userId,
    loginTimestamp = session.timestamp,
    loginResult = session.login,
    musicId = 363,
    level = 1,
    achievement = 1000000,
    dxScore = 100,
)
```

## 说明

Kotlin 版目前采用“核心结构强类型 + 协议大包 Map 化”的方式。这样能先快速对齐旧逻辑，后续再把稳定的协议块逐步替换成 data class。
