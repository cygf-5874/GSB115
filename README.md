# sortstable

Java 17 的**多键稳定排序**小库。只用 JDK 标准库（`javac` / `java`），不用 Maven / Gradle，
不引入任何第三方依赖。

```java
List<Key> keys = List.of(new Key("group", true), new Key("score", false));
List<Row> sorted = MultiSort.sort(rows, keys);
```

`rows` 是一批 `Row`（`id` + 若干具名字段，字段值只会是 `Long` / `String` / `null`）；
`keys` 是按优先级排好的 `Key`（字段名 + 升/降序），`keys[0]` 优先级最高。

## 目录

```
src/main/java/sortstable/MultiSort.java   排序入口（既有实现）
src/main/java/sortstable/Cmp.java         比较计数器（固定入口，勿改）
src/main/java/sortstable/Row.java         一行记录
src/main/java/sortstable/Key.java         一个排序键
src/test/java/sortstable/MultiSortTest.java  既有用例（12 个，手写 runner，无第三方框架）
repro/Repro.java                          复现脚本：打印当前实现的 Cmp.count
scripts/build.sh                          编译 main / repro / test
scripts/test.sh                           跑既有用例
scripts/check.sh                          固定验收入口（勿改）
check/Checker.java                        固定验收程序（勿改）
```

## 语言版本前提

- Java 17（`javac` / `java`）。
- 只用 JDK 标准库；构建产物在 `out/`、`out-test/`、`out-check/`，已在 `.gitignore` 中忽略。

## 怎么跑

```bash
bash scripts/build.sh      # 编译
bash scripts/test.sh       # 既有用例（12 个）
bash scripts/check.sh      # 固定验收；支持 -list 与 --only <组名>
bash scripts/check.sh -list
bash scripts/check.sh --only stable
java -cp out Repro 2000 2  # 复现脚本：n=2000、2 个键时的比较次数
```

## 对外契约

下面 8 条是 `sortstable` 的**对外契约**，它们是本题验收点的唯一出处。
**它们是契约，不是「当前实现的行为」的转述**；实现方式不限，但必须让这 8 条同时成立。

1. **排序入口**：`MultiSort.sort(List<Row> rows, List<Key> keys)` 按 `keys` 给定的优先级排序，
   `Key` 含字段名与升/降序（`keys[0]` 优先级最高）。
2. **必须稳定**：所有键都相等的两行，输出里必须保持它们**在输入里的先后顺序**。
3. **比较语义**：值按类型比较 —— `Long` 按数值、`String` 按 `String.compareTo`（码点序，
   **不得**用 `Collator`，与默认 Locale 无关）；不同类型的值相遇抛 `IllegalArgumentException`。
4. **`null` 排在最前**：`null` 值排在非 `null` **之前**，且这一点**与升序 / 降序无关**
   （降序时 `null` 仍然在前面）。多个 `null` 之间保持输入顺序。
5. **比较计数**：`Cmp.count` 统计**键比较次数** —— 每调用一次 `Cmp.compare` 计一次。
   所有键比较都必须经过 `Cmp.compare`，不得绕开计数器；判据不依赖墙钟。
6. **规模判据**：`n = 100_000` 行、2 个键时的 `Cmp.count` 必须 **< 4_000_000**。
   这条上界对**任意键数**都成立（`check/` 用 2 个键与 5 个键各测一次，上界相同）——
   也就是比较次数不得随键数线性增长。
7. **不改入参**：结果与输入 `List` 的可变性无关 —— `sort` 不得修改调用方传入的列表，
   必须返回一个新的列表；返回的列表与入参不是同一个对象。
8. **边界**：允许 `rows` 为空或单元素；`keys` 为空时按**原始顺序**返回。

## 本次重构的目标

- **目标 1**：把契约第 6 条的 `Cmp.count` 压下来 —— 现状是十万元素要跑四秒多，
  比较次数在 1e10 量级。
- **目标 2**：契约第 2 条的**稳定性**与第 3、4 条的**比较语义**，重构前后一字不变。
- **目标 3**：契约第 5、7、8 条照旧成立（计数口径不变、不改入参、边界不抛异常）。

> 可以新增类与内部结构，但公开的 API 名与签名不许改；`check/`、
> `src/test/java/sortstable/MultiSortTest.java` 里的既有用例都是契约的一部分，勿改。

## 验收

`check/Checker.java` 是固定验收程序，**不要修改**。它按 4 组共 8 个场景检查上面的契约。
**每个场景都在独立 JVM 子进程里跑，带看门狗**（默认 30 秒，`cost` 组 60 秒），
超时判不通过；一个场景失败不会遮蔽其余场景（失败不早退）。

| 组 | 场景 | 对应契约 |
| --- | --- | --- |
| `semantics` | `ascending-numeric-and-string` | 1、3 |
| `semantics` | `descending` | 1、2、3 |
| `semantics` | `null-first-both-directions` | 4 |
| `stable` | `ties-keep-input-order` | 2 |
| `stable` | `all-keys-equal` | 2、8 |
| `cost` | `two-keys-100k` | 5、6 |
| `cost` | `many-keys-100k` | 5、6 |
| `edge` | `empty-single-no-keys-and-purity` | 7、8 |

两个 `cost` 场景都用 **`n = 100_000`** 与**同一个上界 4_000_000**：
`two-keys-100k` 用 2 个键，`many-keys-100k` 用 5 个键。规模数据由写死的 splitmix64 生成，
跨 JVM、跨平台逐位一致；判据读的是 `Cmp.count`，不是耗时
（硬上限 40_000_000 只是防止失控的实现把整轮验收拖死，正常实现远够不着）。
