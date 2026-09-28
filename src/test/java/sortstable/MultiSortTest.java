package sortstable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sortstable 的既有用例（12 个），覆盖 README「对外契约」的语义部分。
 *
 * <p>不依赖 JUnit：静态方法加 {@code main} 聚合自跑，全部通过时打印 {@code 12/12 passed}
 * 并以退出码 0 结束；有失败时打印失败清单并以退出码 1 结束。
 *
 * <p>这些断言只说**行为**，不关心比较次数、也不关心用什么排序算法 —— 重构前后都必须绿。
 */
public final class MultiSortTest {

    private interface Body {
        /** 返回 null 表示通过，否则返回失败原因。 */
        String run();
    }

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("one-key-asc-numeric", MultiSortTest::testOneKeyAscNumeric);
        run("one-key-desc-numeric", MultiSortTest::testOneKeyDescNumeric);
        run("one-key-asc-string", MultiSortTest::testOneKeyAscString);
        run("two-keys-mixed-direction", MultiSortTest::testTwoKeysMixed);
        run("three-keys", MultiSortTest::testThreeKeys);
        run("null-first-ascending", MultiSortTest::testNullFirstAsc);
        run("null-first-descending", MultiSortTest::testNullFirstDesc);
        run("stable-equal-leading-key", MultiSortTest::testStableLeading);
        run("stable-all-keys-equal", MultiSortTest::testStableAllEqual);
        run("empty-and-single-rows", MultiSortTest::testEmptyAndSingle);
        run("empty-keys-keeps-input-order", MultiSortTest::testEmptyKeys);
        run("input-list-not-modified", MultiSortTest::testInputNotModified);

        System.out.println(passed + "/12 passed");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void run(String name, Body body) {
        try {
            String err = body.run();
            if (err == null) {
                passed++;
            } else {
                failures.add(name + "：" + err);
            }
        } catch (Throwable t) {
            failures.add(name + "：抛出 " + t);
        }
    }

    // ------------------------------------------------------------------ 用例

    private static String testOneKeyAscNumeric() {
        List<Row> rows = rows(
                r("a", "n", 3L),
                r("b", "n", 1L),
                r("c", "n", 2L));
        List<Row> out = MultiSort.sort(rows, keys(k("n", true)));
        return expectIds("单键升序", out, "b", "c", "a");
    }

    private static String testOneKeyDescNumeric() {
        List<Row> rows = rows(
                r("a", "n", 3L),
                r("b", "n", 1L),
                r("c", "n", 2L));
        List<Row> out = MultiSort.sort(rows, keys(k("n", false)));
        return expectIds("单键降序", out, "a", "c", "b");
    }

    private static String testOneKeyAscString() {
        List<Row> rows = rows(
                r("a", "s", "banana"),
                r("b", "s", "apple"),
                r("c", "s", "cherry"));
        List<Row> out = MultiSort.sort(rows, keys(k("s", true)));
        return expectIds("单键字符串升序（compareTo，区域无关）", out, "b", "a", "c");
    }

    private static String testTwoKeysMixed() {
        List<Row> rows = rows(
                r("a", "g", 2L, "s", "x"),
                r("b", "g", 1L, "s", "y"),
                r("c", "g", 2L, "s", "w"),
                r("d", "g", 1L, "s", "x"));
        // g 升序、s 降序
        List<Row> out = MultiSort.sort(rows, keys(k("g", true), k("s", false)));
        return expectIds("两键（g 升 / s 降）", out, "b", "d", "a", "c");
    }

    private static String testThreeKeys() {
        List<Row> rows = rows(
                r("a", "g", 1L, "h", 2L, "s", "m"),
                r("b", "g", 1L, "h", 1L, "s", "z"),
                r("c", "g", 1L, "h", 1L, "s", "a"),
                r("d", "g", 0L, "h", 9L, "s", "q"));
        List<Row> out = MultiSort.sort(rows, keys(k("g", true), k("h", true), k("s", true)));
        return expectIds("三键升序", out, "d", "c", "b", "a");
    }

    private static String testNullFirstAsc() {
        List<Row> rows = rows(
                r("a", "n", 5L),
                r("b", "n", null),
                r("c", "n", 1L));
        List<Row> out = MultiSort.sort(rows, keys(k("n", true)));
        return expectIds("升序时 null 排在最前", out, "b", "c", "a");
    }

    private static String testNullFirstDesc() {
        List<Row> rows = rows(
                r("a", "n", 5L),
                r("b", "n", null),
                r("c", "n", 1L));
        List<Row> out = MultiSort.sort(rows, keys(k("n", false)));
        return expectIds("降序时 null 仍排在最前", out, "b", "a", "c");
    }

    private static String testStableLeading() {
        List<Row> rows = rows(
                r("a", "g", 1L, "n", 3L),
                r("b", "g", 1L, "n", 1L),
                r("c", "g", 1L, "n", 2L),
                r("d", "g", 0L, "n", 9L));
        // g 相等时按 n 升序，n 也相等时保持输入顺序（这里没有第三键，所以看不到，
        // 但 g 相同且 n 相同的行仍要按输入顺序排）
        List<Row> out = MultiSort.sort(rows, keys(k("g", true), k("n", true)));
        return expectIds("首键相等时看次键", out, "d", "b", "c", "a");
    }

    private static String testStableAllEqual() {
        List<Row> rows = rows(
                r("a", "g", 7L, "s", "same"),
                r("b", "g", 7L, "s", "same"),
                r("c", "g", 7L, "s", "same"),
                r("d", "g", 7L, "s", "same"),
                r("e", "g", 7L, "s", "same"));
        List<Row> out = MultiSort.sort(rows, keys(k("g", true), k("s", true)));
        return expectIds("所有键都相等时保持输入顺序", out, "a", "b", "c", "d", "e");
    }

    private static String testEmptyAndSingle() {
        List<Row> empty = rows();
        List<Row> out = MultiSort.sort(empty, keys(k("n", true)));
        String e = expectIds("空列表", out, new String[0]);
        if (e != null) {
            return e;
        }
        List<Row> one = rows(r("only", "n", 4L));
        out = MultiSort.sort(one, keys(k("n", true)));
        return expectIds("单元素", out, "only");
    }

    private static String testEmptyKeys() {
        List<Row> rows = rows(
                r("a", "n", 3L),
                r("b", "n", 1L),
                r("c", "n", 2L));
        List<Row> out = MultiSort.sort(rows, keys());
        return expectIds("keys 为空时按原始顺序返回", out, "a", "b", "c");
    }

    private static String testInputNotModified() {
        List<Row> rows = rows(
                r("a", "n", 3L),
                r("b", "n", 1L),
                r("c", "n", 2L));
        List<Row> out = MultiSort.sort(rows, keys(k("n", true)));
        String e = expectIds("返回列表已排好", out, "b", "c", "a");
        if (e != null) {
            return e;
        }
        if (out == rows) {
            return "期望=返回新列表 实际=返回的就是入参同一个对象";
        }
        return expectIds("入参列表本身没有被改动", rows, "a", "b", "c");
    }

    // ------------------------------------------------------------------ 工具

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

    private static String expectIds(String what, List<Row> actual, String... wanted) {
        List<String> got = new ArrayList<>();
        for (Row row : actual) {
            got.add(row.id());
        }
        List<String> want = Arrays.asList(wanted);
        if (!want.equals(got)) {
            return "期望=" + what + " → " + want + " 实际=" + got;
        }
        return null;
    }

    private MultiSortTest() {
    }
}
