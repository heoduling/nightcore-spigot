# 全量源码构建

需要 Python 3.9+、`curl`、Maven 3.9.11 和 JDK 25。将 `JAVA_HOME` 指向 JDK，并将 Maven 加入 `PATH`。

在仓库根目录运行以下 PowerShell 命令。

```powershell
$repo = Join-Path $env:TEMP 'nightcore-build-m2'
python tools/full-build/bootstrap.py --repository $repo
mvn "-Dmaven.repo.local=$repo" "-Dmaven.compiler.fork=true" "-Dmaven.compiler.executable=$env:JAVA_HOME/bin/javac.exe" -o -B -ntp clean verify
```

其他 shell 可将同一个本地仓库路径分别传给 `--repository` 和 `-Dmaven.repo.local`。

输出插件为 `target/nightcore-2.16.1.jar`。六个源码模块为 `utils`、`bridge`、`paper`、`spigot`、`intergration-placeholderapi` 和 `main`。

## 依赖清单

`dependencies.json` 固定了 605 个公开原始 JAR/POM 的下载 URL、SHA-256、大小和 Maven 相对路径，覆盖编译依赖、Maven 构建插件及 JUnit 运行器。SNAPSHOT 使用对应版本的时间戳下载地址。构件坐标与原 POM 一致。

脚本逐个验证已有缓存，复用哈希匹配的文件，对缺失或损坏文件下载到临时文件，验证成功后原子替换。随后写入 Maven 本地构件标记，使上述离线构建命令可独立使用指定仓库。首次执行需要访问清单中的公开仓库，后续执行继续校验缓存。

仓库自带的两个真实插件依赖仍由原 POM 的 `systemPath` 引用。

| 文件 | SHA-256 |
| --- | --- |
| `main/lib/ExcellentCrates-6.6.1.jar` | `cb7ba37ddb04ebd481b5cccb282db497ff4f9695d1e4f8e8367c05124e8c0944` |
| `main/lib/craft-engine-paper-plugin-26.5-SNAPSHOT.jar` | `6a2758b67ba793804860c6f06bd2cd5f6a084b6c216aad0c9781bbc1c9e7cae0` |

## 构建验证

2026-11-09 已在全新独立 Maven 仓库中，从清单的公开 URL 下载并验证全部 605 个文件，随后完成上述全 reactor `clean verify`。

- 725 份生产 Java 源码全部进入正常 Maven 编译输入。
- 插件中的 821 个 class 与六个模块本次 `target/classes` 输出逐字节一致，class-file major version 为 65。
- foliafix-4 的 29 项单元测试全部通过，失败、错误和跳过数量均为零。
- 单元测试包括 `CoreManagerMenuRefreshTest` 6 项、`DialogQuitLifecycleTest` 15 项、`DialogTimeoutTest` 5 项、`PlayerBlockTrackerTest` 3 项。
- 再次执行 bootstrap 复用全部 605 个缓存文件。人为损坏一个隔离缓存文件后，脚本重新下载该文件，其余 604 个继续复用。

本次 JDK 25 的 Maven 进程内编译在测试编译阶段出现 javac 诊断格式化内部异常 `this.hashes is null`。上面的命令显式使用独立 javac 进程，已经从 `clean` 开始重新编译全部模块并通过测试。首次异常日志和最终成功日志均随审查证据保留。

## 实际核心验证依赖

命令注册、别名映射和卸载行为的运行时验收使用独立服务器与实际核心。对应核心报告的版本为 `mizuki 26.2-DEV-codex/vanilla-generation-ticket-fix-01a19c50@96ca96b`，Minecraft 版本为 `26.2`，API 为 `26.2-SNAPSHOT`。

| 验证输入 | SHA-256 |
| --- | --- |
| `Shiroha-26.2-generation-tickets.jar` 启动包 | `19bac68bdd71810a4417b69acdc4b82f41a3e4d80065075f3d42ae87179f2b85` |
| `versions/26.2/shiroha-26.2.jar` 实际服务端 | `40a178a02a2d5810596bab1e75df2c585760e58306e6274cb28328e361655f2a` |

运行时探针还使用该服务器配套的 `libraries/`、JDK 25 及本次源码构建的 NightCore。复核命令映射行为时应匹配以上实际核心，因为其 `ForwardingMap` 视图实现参与注销操作。Maven `verify` 执行的是上列 29 项单元测试，运行时探针单独执行。
