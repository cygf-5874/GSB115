十万元素的多键排序要跑四秒多，其中大部分时间花在重复比较上。sortstable 是 Java 17 的
多键排序库，只用 JDK 标准库（javac/java），构建走 `scripts/build.sh`，
自检走 `scripts/check.sh`（`check/` 是固定验收程序，别改），既有用例走 `bash scripts/test.sh`。

`MultiSort.java` 里那份实现是对的，但它把每个键拆成独立的一趟排序。
判据是**比较次数**不是墙钟：`Cmp.count` 计数器已给全，`check/` 会按规模卡上界。

任务：改成比较次数更低的实现，同时守住 README 里的 8 条契约。

验收：
- bash scripts/test.sh 全绿；
- bash scripts/check.sh 退出码 0，8 个场景全过（semantics 3 + stable 2 + cost 2 + edge 1）。

约束：
1. 不改 `check/`、不改 `Cmp.count` 的名字与含义；可以新增类。
2. 对外 API 名与签名不许改；`MultiSortTest.java` 里的既有用例一条都不许删或改。
3. 不许引入任何第三方依赖，不许加 `pom.xml` / `build.gradle`。
4. 不许修改调用方传进来的列表。
