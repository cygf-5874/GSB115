import sortstable.Cmp;
import sortstable.Key;
import sortstable.MultiSort;
import sortstable.Row;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * sortstable 的固定验收程序（勿改）。
 *
 * <p>8 个场景覆盖 README「对外契约」：semantics 3 / stable 2 / cost 2 / edge 1。
 * 每个场景都在**独立 JVM 子进程**里跑（{@link ProcessBuilder}），带看门狗，超时判不通过；
 * 某个场景失败不会遮蔽其余场景（失败不早退）。
 *
 * <p>判据全部确定性：没有随机源（规模数据由写死的 splitmix64 生成）、没有墙钟
 * （cost 组读的是 {@link Cmp#count}，不是耗时；硬上限只是防止失控实现把整轮验收拖死）。
 *
 * <p>用法：java -cp out;out-check Checker [-list] [--only &lt;组名&gt;[,&lt;组名&gt;...]]
 */
public final class Checker {

    /** 单场景看门狗默认值（秒）。 */
    private static final long WATCHDOG_SECONDS = 30L;
    /** cost 场景的看门狗（秒）：比默认宽一些，够它跑到硬上限。 */
    private static final long COST_WATCHDOG_SECONDS = 60L;

    /** 契约第 6 条的比较次数上界。 */
    private static final long COST_BOUND = 4_000_000L;
    /** 硬上限：比较次数一旦超过它，就直接判超预算，不必等失控的实现跑完。 */
    private static final long COST_HARD_CAP = 40_000_000L;

    /** 规模场景的行数。 */
    private static final int COST_N = 100_000;

    /** 写死的随机种子，保证跨 JVM、跨平台一致。 */
    private static final long SEED = 0x243F6A8885A308D3L;

    private interface Body {
        /** 返回 null 表示 PASS，否则返回可判定的失败原因（内含 期望=… 实际=…）。 */
        String run() throws Exception;
    }

    private static final class Scenario {
        final String name;
        final String group;
        final long watchdogSeconds;
        final Body body;

        Scenario(String name, Body body) {
            this(name, WATCHDOG_SECONDS, body);
        }

        Scenario(String name, long watchdogSeconds, Body body) {
            this.name = name;
            this.body = body;
            this.watchdogSeconds = watchdogSeconds;
            int slash = name.indexOf('/');
            this.group = slash < 0 ? name : name.substring(0, slash);
        }
    }

    public static void main(String[] args) throws Exception {
        String scenarioOnly = null;
        List<String> onlyGroups = new ArrayList<>();
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-list".equals(a)) {
                list = true;
            } else if ("--only".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--only 需要一个组名");
                    System.exit(2);
                }
                for (String g : args[++i].split(",")) {
                    if (!g.isEmpty()) {
                        onlyGroups.add(g);
                    }
                }
            } else if ("--scenario".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--scenario 需要一个场景名");
                    System.exit(2);
                }
                scenarioOnly = args[++i];
            } else {
                System.out.println("未知参数：" + a);
                System.exit(2);
            }
        }

        List<Scenario> all = scenarios();

        if (list) {
            for (Scenario s : all) {
                System.out.println(s.name);
            }
            return;
        }

        if (scenarioOnly != null) {
            System.exit(runOne(all, scenarioOnly));
        }

        List<Scenario> selected = new ArrayList<>();
        for (Scenario s : all) {
            if (onlyGroups.isEmpty() || onlyGroups.contains(s.group)) {
                selected.add(s);
            }
        }
        if (selected.isEmpty()) {
            System.out.println("没有匹配的场景（--only " + onlyGroups + "）");
            System.exit(2);
        }

        int pass = 0;
        for (Scenario s : selected) {
            String line = runInChildProcess(s);
            System.out.println(line);
            if (line.startsWith("PASS ")) {
                pass++;
            }
        }
        System.out.println("结果：通过 " + pass + "/" + selected.size());
        if (pass != selected.size()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------- 子进程调度（一场景一 JVM）

    /** 在独立子进程里跑单个场景，把子进程给出的 PASS/FAIL 行原样返回。 */
    private static String runInChildProcess(Scenario s) {
        String name = s.name;
        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(javaBin,
                "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", classpath, "Checker", "--scenario", name);
        pb.redirectErrorStream(true);

        Process proc;
        try {
            proc = pb.start();
        } catch (Exception ex) {
            return "FAIL " + name + "  期望=能在独立 JVM 子进程里跑该场景 实际=子进程启动失败：" + ex;
        }

        StringBuilder sink = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = proc.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    synchronized (sink) {
                        sink.append(chunk);
                    }
                }
            } catch (Exception ignored) {
                // 子进程被强杀时读流会失败，忽略即可
            }
        }, "checker-child-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = proc.waitFor(s.watchdogSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            proc.destroyForcibly();
            try {
                reader.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "FAIL " + name + "  期望=场景在 " + s.watchdogSeconds + " 秒内给出 PASS/FAIL 实际="
                    + s.watchdogSeconds + " 秒看门狗超时（子进程已强杀）";
        }
        try {
            reader.join(5000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        String out;
        synchronized (sink) {
            out = sink.toString().trim();
        }
        String line = null;
        for (String l : out.split("\n")) {
            String t = l.trim();
            if (t.startsWith("PASS ") || t.startsWith("FAIL ")) {
                line = t;
            }
        }
        if (line == null) {
            String shown = out.isEmpty() ? "(无输出)" : out.replace('\n', ' ');
            return "FAIL " + name + "  期望=子进程打印 PASS/FAIL 行 实际=退出码 "
                    + (proc.isAlive() ? "未知（仍存活）" : String.valueOf(proc.exitValue())) + "，输出=" + shown;
        }
        return line;
    }

    /** 子进程模式：就地跑一个场景，打印一行 PASS/FAIL，返回退出码。 */
    private static int runOne(List<Scenario> all, String name) {
        for (Scenario s : all) {
            if (!s.name.equals(name)) {
                continue;
            }
            String err;
            try {
                err = s.body.run();
            } catch (Throwable t) {
                err = "检查过程抛异常：" + t;
            }
            if (err == null) {
                System.out.println("PASS " + s.name);
                return 0;
            }
            if (!err.contains("期望=")) {
                err = "期望=场景 " + s.name + " 通过 实际=" + err;
            }
            System.out.println("FAIL " + s.name + "  " + err);
            return 1;
        }
        System.out.println("FAIL " + name + "  期望=场景名存在于固定件 实际=未找到该场景");
        return 1;
    }

    // ------------------------------------------------------------- 场景表

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();

        // ------------------------------------------------ semantics（3）

        out.add(new Scenario("semantics/ascending-numeric-and-string", () -> {
            List<Row> rows = rows(
                    r("a", "n", 3L, "s", "banana"),
                    r("b", "n", 1L, "s", "apple"),
                    r("c", "n", 2L, "s", "cherry"),
                    r("d", "n", 2L, "s", "avocado"));
            String e = expectIds("按 n 升序", sortIds(rows, keys(k("n", true))), "b", "c", "d", "a");
            if (e != null) {
                return e;
            }
            return expectIds("按 s 升序（String.compareTo，区域无关）",
                    sortIds(rows, keys(k("s", true))), "b", "d", "a", "c");
        }));

        out.add(new Scenario("semantics/descending", () -> {
            List<Row> rows = rows(
                    r("a", "n", 3L, "s", "banana"),
                    r("b", "n", 1L, "s", "apple"),
                    r("c", "n", 2L, "s", "cherry"),
                    r("d", "n", 2L, "s", "avocado"));
            String e = expectIds("按 n 降序", sortIds(rows, keys(k("n", false))), "a", "c", "d", "b");
            if (e != null) {
                return e;
            }
            e = expectIds("按 (n 升, s 降)",
                    sortIds(rows, keys(k("n", true), k("s", false))), "b", "c", "d", "a");
            if (e != null) {
                return e;
            }
            return expectIds("按 (n 降, s 升)",
                    sortIds(rows, keys(k("n", false), k("s", true))), "a", "d", "c", "b");
        }));

        out.add(new Scenario("semantics/null-first-both-directions", () -> {
            List<Row> rows = rows(
                    r("a", "n", 5L),
                    r("b", "n", (Object) null),
                    r("c", "n", 1L),
                    r("d", "n", (Object) null),
                    r("e", "n", 9L));
            String e = expectIds("升序时 null 在前且保持输入顺序",
                    sortIds(rows, keys(k("n", true))), "b", "d", "c", "a", "e");
            if (e != null) {
                return e;
            }
            e = expectIds("降序时 null 仍在前（不受方向影响）",
                    sortIds(rows, keys(k("n", false))), "b", "d", "e", "a", "c");
            if (e != null) {
                return e;
            }
            List<Row> srows = rows(
                    r("a", "s", "m"),
                    r("b", "s", (Object) null),
                    r("c", "s", "z"));
            return expectIds("字符串键降序时 null 仍在前",
                    sortIds(srows, keys(k("s", false))), "b", "c", "a");
        }));

        // ------------------------------------------------ stable（2）

        out.add(new Scenario("stable/ties-keep-input-order", () -> {
            List<Row> rows = new ArrayList<>();
            // 首键只有两个取值，制造大片并列；次键也大量并列
            for (int i = 0; i < 12; i++) {
                rows.add(r("r" + i, "g", (long) (i % 2), "h", (long) (i % 3)));
            }
            List<Key> keys = keys(k("g", true), k("h", true));
            List<String> got = sortIds(rows, keys);
            List<String> want = new ArrayList<>();
            for (int g = 0; g <= 1; g++) {
                for (int h = 0; h <= 2; h++) {
                    for (int i = 0; i < 12; i++) {
                        if (i % 2 == g && i % 3 == h) {
                            want.add("r" + i);
                        }
                    }
                }
            }
            if (!want.equals(got)) {
                return "期望=并列行按输入顺序 → " + want + " 实际=" + got;
            }
            return null;
        }));

        out.add(new Scenario("stable/all-keys-equal", () -> {
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                rows.add(r("r" + i, "g", 7L, "s", "same"));
            }
            List<String> got = sortIds(rows, keys(k("g", true), k("s", true)));
            List<String> want = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                want.add("r" + i);
            }
            if (!want.equals(got)) {
                return "期望=所有键相等时输出与输入同序 → " + want + " 实际=" + got;
            }
            // 同一批数据，键全为空列表时也必须原序返回
            List<String> none = sortIds(rows, keys());
            if (!want.equals(none)) {
                return "期望=keys 为空时输出与输入同序 → " + want + " 实际=" + none;
            }
            return null;
        }));

        // ------------------------------------------------ cost（2）

        out.add(new Scenario("cost/two-keys-100k", COST_WATCHDOG_SECONDS, () -> costScenario(2)));
        out.add(new Scenario("cost/many-keys-100k", COST_WATCHDOG_SECONDS, () -> costScenario(5)));

        // ------------------------------------------------ edge（1）

        out.add(new Scenario("edge/empty-single-no-keys-and-purity", () -> {
            List<Row> emptyRows = rows();
            List<String> e1 = sortIds(emptyRows, keys(k("n", true)));
            if (!e1.isEmpty()) {
                return "期望=空列表排序后仍为空 实际=" + e1;
            }
            List<Row> one = rows(r("only", "n", 4L));
            List<String> e2 = sortIds(one, keys(k("n", true)));
            if (!Arrays.asList("only").equals(e2)) {
                return "期望=单元素列表排序后仍是它 实际=" + e2;
            }
            List<Row> rows = rows(
                    r("a", "n", 3L),
                    r("b", "n", 1L),
                    r("c", "n", 2L));
            List<Row> sorted = MultiSort.sort(rows, keys(k("n", true)));
            if (sorted == null) {
                return "期望=sort 返回非 null 的列表 实际=null";
            }
            if (sorted == rows) {
                return "期望=sort 返回新的列表对象（不改入参） 实际=返回的就是入参对象";
            }
            List<String> before = ids(rows);
            if (!Arrays.asList("a", "b", "c").equals(before)) {
                return "期望=调用后入参列表顺序不变 实际=" + before;
            }
            List<String> none = sortIds(rows, keys());
            if (!Arrays.asList("a", "b", "c").equals(none)) {
                return "期望=keys 为空时按原始顺序返回 实际=" + none;
            }
            return null;
        }));

        return out;
    }

    // ------------------------------------------------------------- cost 场景

    private static String costScenario(int keyCount) throws Exception {
        List<Row> rows = costRows(COST_N, keyCount);
        List<Key> keys = costKeys(keyCount);

        Holder h = runCapped(rows, keys);
        if (h.error != null) {
            return h.error;
        }
        if (h.count < rows.size() - 1L) {
            return "期望=排序真的在比较键（Cmp.count ≥ n-1） 实际=" + h.count
                    + "（键比较必须经过 Cmp.compare）";
        }
        if (h.count >= COST_BOUND) {
            return "期望=n=" + COST_N + " 行 " + keyCount + " 个键时 Cmp.count < " + COST_BOUND
                    + " 实际=" + h.count;
        }
        return verifySortedStable(rows, h.rows, keys, "cost/" + keyCount + "keys");
    }

    /** 在守护线程里跑被测 sort，同时盯着 Cmp.count；超过硬上限就提前判超预算。 */
    private static Holder runCapped(List<Row> rows, List<Key> keys) throws Exception {
        Holder h = new Holder();
        Cmp.reset();
        Thread t = new Thread(() -> {
            try {
                h.rows = MultiSort.sort(rows, keys);
            } catch (Throwable ex) {
                h.error = "期望=排序正常返回 实际=抛了 " + ex;
            }
        }, "sortstable-cost");
        t.setDaemon(true);
        t.start();
        while (t.isAlive() && Cmp.count <= COST_HARD_CAP) {
            Thread.sleep(1L);
        }
        if (t.isAlive()) {
            h.error = "期望=n=" + COST_N + " 行时 Cmp.count < " + COST_BOUND
                    + " 实际=比较次数已超过硬上限 " + COST_HARD_CAP + "（排序远未跑完，提前判定超预算）";
            return h;
        }
        t.join();
        h.count = Cmp.count;
        return h;
    }

    private static final class Holder {
        List<Row> rows;
        String error;
        long count;
    }

    /** 规模数据：写死的 splitmix64 生成，跨 JVM 逐位一致。 */
    private static List<Row> costRows(int n, int keyCount) {
        Rng rng = new Rng(SEED);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Object> f = new LinkedHashMap<>();
            // 首键取值多，保证单趟比较器在大多数元素比较上只看一两个键
            f.put("k0", (long) rng.nextInt(1_000_000));
            for (int j = 1; j < keyCount; j++) {
                f.put("k" + j, "s" + rng.nextInt(4096));
            }
            rows.add(new Row("r" + i, f));
        }
        return rows;
    }

    private static List<Key> costKeys(int keyCount) {
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add(new Key("k" + i, i % 2 == 0));
        }
        return keys;
    }

    // ------------------------------------------------------------- 校验工具

    /** 排序并把结果映射成 id 列表；排序抛异常时抛出（由场景捕获）。 */
    private static List<String> sortIds(List<Row> rows, List<Key> keys) {
        List<Row> out = MultiSort.sort(rows, keys);
        return ids(out);
    }

    private static List<String> ids(List<Row> rows) {
        List<String> out = new ArrayList<>();
        for (Row row : rows) {
            out.add(row.id());
        }
        return out;
    }

    /** 校验输出：恰好含输入里的每一行一次、按 keys 非降序、键相等者保持输入顺序。 */
    private static String verifySortedStable(List<Row> in, List<Row> out, List<Key> keys, String what) {
        if (out == null) {
            return "期望=" + what + " 返回非 null 的列表 实际=null";
        }
        if (out.size() != in.size()) {
            return "期望=" + what + " 输出行数 " + in.size() + " 实际=" + out.size();
        }
        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < in.size(); i++) {
            pos.put(in.get(i).id(), i);
        }
        Set<String> seen = new HashSet<>();
        for (Row row : out) {
            if (row == null) {
                return "期望=" + what + " 输出元素非 null 实际=含 null";
            }
            if (!pos.containsKey(row.id())) {
                return "期望=" + what + " 输出只含输入里的行 实际=出现 " + row.id();
            }
            if (!seen.add(row.id())) {
                return "期望=" + what + " 每行恰好出现一次 实际=" + row.id() + " 出现多次";
            }
        }
        if (seen.size() != in.size()) {
            return "期望=" + what + " 输出含全部输入行 实际=只有 " + seen.size() + " 行";
        }
        for (int i = 0; i + 1 < out.size(); i++) {
            Row x = out.get(i);
            Row y = out.get(i + 1);
            int c = compareContract(x, y, keys);
            if (c > 0) {
                return "期望=" + what + " 按 keys 非降序 实际=第 " + i + " 行 " + x.id()
                        + " 与第 " + (i + 1) + " 行 " + y.id() + " 次序颠倒";
            }
            if (c == 0 && pos.get(x.id()) > pos.get(y.id())) {
                return "期望=" + what + " 键相等的两行保持输入顺序 实际=" + x.id()
                        + "（原 #" + pos.get(x.id()) + "）排在 " + y.id() + "（原 #" + pos.get(y.id()) + "）之前";
            }
        }
        return null;
    }

    /** 契约语义下的比较（不经过 Cmp，不影响计数）。 */
    private static int compareContract(Row x, Row y, List<Key> keys) {
        for (Key key : keys) {
            Object a = x.get(key.field());
            Object b = y.get(key.field());
            int c;
            if (a == null && b == null) {
                c = 0;
            } else if (a == null) {
                c = -1;
            } else if (b == null) {
                c = 1;
            } else if (a instanceof Long && b instanceof Long) {
                c = Long.compare((Long) a, (Long) b);
            } else if (a instanceof String && b instanceof String) {
                c = ((String) a).compareTo((String) b);
            } else {
                throw new IllegalArgumentException("键值类型不一致：" + a.getClass() + " vs " + b.getClass());
            }
            if (c != 0) {
                return (a == null || b == null) ? c : (key.ascending() ? c : -c);
            }
        }
        return 0;
    }

    private static String expectIds(String what, List<String> got, String... wanted) {
        List<String> want = Arrays.asList(wanted);
        if (!want.equals(got)) {
            return "期望=" + what + " → " + want + " 实际=" + got;
        }
        return null;
    }

    private static Row r(String id, Object... kv) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            fields.put((String) kv[i], kv[i + 1]);
        }
        return new Row(id, fields);
    }

    private static List<Row> rows(Row... rs) {
        return new ArrayList<>(Arrays.asList(rs));
    }

    private static Key k(String field, boolean asc) {
        return new Key(field, asc);
    }

    private static List<Key> keys(Key... ks) {
        return new ArrayList<>(Arrays.asList(ks));
    }

    /** 写死的 splitmix64：与 JDK 内部实现无关，跨 JVM 逐位一致。 */
    private static final class Rng {
        private long s;

        Rng(long seed) {
            this.s = seed;
        }

        long next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        int nextInt(int bound) {
            return (int) ((next() >>> 1) % bound);
        }
    }

    private Checker() {
    }
}
