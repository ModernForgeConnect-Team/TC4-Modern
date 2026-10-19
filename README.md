# TC4: Modern

TC Modern Lib/API 与 TC4: Modern 共用一个仓库，以两个独立 Gradle 子模块开发。
当前是可构建的工程起点，已接通 MFCL 的公开生命周期 API；尚未迁移研究、扫描、配方或渲染玩法。

| 模块 | 职责 | Forge mod ID |
| --- | --- | --- |
| `tc-modern-lib` | 共享 API、旧 TC4 访问与后续兼容实现 | `tcmodernlib` |
| `tc4-modern` | 玩法、内容与现代侧集成，直接依赖同仓库 Lib | `tc4modern` |

依赖方向：`TC4: Modern → TC Modern Lib/API → MFCL public API`。
MFCL 仍是独立项目，不引用其 `internal`、`bridge` 或 `compat` 实现，不将旧 Minecraft / Forge / TC4 类加入现代编译类路径。

## 环境与首次构建

- JDK 17，Minecraft 1.20.1，Forge 47.4.28，Gradle Wrapper 8.8。
- Python 3.10+ 用于首次准备独立 MFCL API；已有官方 API JAR 时可跳过 Python。
- IntelliJ IDEA 打开根目录，Gradle JVM 选择 JDK 17。

Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
python tools/prepare_mfcl_api.py
.\gradlew.bat build
```

Linux/macOS：

```sh
export JAVA_HOME=/path/to/jdk-17
python3 tools/prepare_mfcl_api.py
bash gradlew build
```

准备脚本只编译 MFCL 的 11 个公开 API 源文件，校验固定提交源归档的 SHA-256，并在本地 JAR 保留许可证和来源。
固定参考提交：[`2c3a2e2`](https://github.com/gengyoubo/ModernForgeConnectLoader/tree/2c3a2e224c90925cf5f6458e825799b80e7b1deb)。`libs/` 下的 API JAR 不提交版本库，也不打进两个模组产物。
如果自行在 MFCL 仓库执行 `apiJar`，可改用：

```powershell
.\gradlew.bat build '-PmfclApiJar=C:/path/to/mfcl-0.1.0-api.jar'
```

## 联调与独立发布

```powershell
.\gradlew.bat :tc4-modern:runClient '-PmfclRuntimeJar=C:/path/to/mfcl-0.1.0.jar' '-PgameDir=C:/path/to/prepared-game'
.\gradlew.bat :tc4-modern:runServer '-PmfclRuntimeJar=C:/path/to/mfcl-0.1.0.jar' '-PgameDir=C:/path/to/prepared-server'
```

开发运行会加载两个模块的源码。`mfclRuntimeJar` 必须是完整 MFCL JAR，不能用 API JAR 代替。
游戏目录需按 [MFCL 文档](https://github.com/gengyoubo/ModernForgeConnectLoader) 准备旧运行时，并将原始 Thaumcraft 4.2.3.5、Baubles 1.0.1.10 放入 `mods/1.7.10/`；这些文件不由本项目下载或分发。
如需分离旧运行时目录，可额外指定 `-PlegacyRuntimeDir=...`。服务端任务仅提供入口，专用服务器兼容性尚未验证。

正式安装时在现代 `mods/` 中分别放入完整 MFCL、`tc-modern-lib` 和 `tc4-modern` 三个模组 JAR。
`implementation project(':tc-modern-lib')` 仅建立工程依赖，不将 Lib 合并进 TC4 JAR。

| 产物 | 路径 |
| --- | --- |
| Lib 完整模组 | `tc-modern-lib/build/libs/tc-modern-lib-0.1.0-SNAPSHOT.jar` |
| Lib 独立 API（仅编译用） | `tc-modern-lib/build/libs/tc-modern-lib-0.1.0-SNAPSHOT-api.jar` |
| TC4 完整模组 | `tc4-modern/build/libs/tc4-modern-0.1.0-SNAPSHOT.jar` |

分别构建：`:tc-modern-lib:build`、`:tc4-modern:build`。根 `build` 构建并检查两个模块。
`publishToMavenLocal` 可发布本地开发产物；独立 API 坐标为 `github.com.gengyoubo.tcmodern:tc-modern-api:0.1.0-SNAPSHOT`，使用者还需显式添加 MFCL API 的 `compileOnly` 依赖。
目前没有配置公共 Maven 发布服务。

## API 起点与边界

```java
TcModernApi.whenReady(runtime -> {
    // 已确认旧运行时开放且实际加载了 Thaumcraft；此时不保证世界或玩家存在。
    // 通过 runtime.accessor(...) / runtime.mappings() 使用 MFCL 公开接口。
});
```

`TcModernApi.runtime()` 每次查询当前 MFCL，关闭后不会返回缓存的旧引用。
就绪回调沿用 MFCL 的线程契约；需要修改世界或玩家时，仍须在所属游戏线程执行。
未加载 TC4 时返回空且不触发 TC4 回调，不伪造游戏兼容成功。

MFCL 中的知识保存、动态物品绘制 SPI、扫描和扫描数据包专用路径尚未全部成为独立公开 API，因此本仓库暂不复制、替换或重复安装这些逻辑。
后续先确定稳定的公开扩展点，再逐步迁移至 Lib；具体玩法留在 `tc4-modern`。
详见上游 [Developer API 与适配边界](https://github.com/gengyoubo/ModernForgeConnectLoader/blob/2c3a2e224c90925cf5f6458e825799b80e7b1deb/docs/developer-api.md)。

## 许可证

本仓库原创代码暂保留所有权利，待仓库所有者选择开源许可证。MFCL API 属于上游 MPL-2.0；本项目不改变第三方文件的许可证。
