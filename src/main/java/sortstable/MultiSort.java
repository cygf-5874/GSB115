package sortstable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多键稳定排序。
 *
 * <p>只用**一趟**稳定排序（JDK 的 TimSort，{@link List#sort}），比较器按
 * {@code keys} 的优先级做字典序比较：首键分出大小就立即返回，首键相等才落到次键。
 * 因此比较次数是 O(n log n)，且不随键数线性增长 —— 大多数行在首键上就分出了大小，
 * 次键及以后基本不会被碰到。每一次键值比较仍走 {@link Cmp#compare}，计数口径不变。
 */
public final class MultiSort {

    /**
     * 按 {@code keys} 给出的优先级排序 {@code rows}（{@code keys[0]}`} 优先级最高），
     * 返回新列表；不修改调用方传入的 {@code rows}。
     */
    public static List<Row> sort(List<Row> rows, List<Key> keys) {
        List<Row> out = new ArrayList<>(rows);
        if (out.size() < 2 || keys.isEmpty()) {
            return out;
        }
        out.sort(lexicographicComparator(keys));
        return out;
    }

    /**
     * 按 {@code keys} 优先级做字典序比较的比较器：从最高优先级键开始，第一个非零结果
     * 即为最终结果；所有键都相等返回 0（交给稳定排序保持输入顺序）。
     */
    private static Comparator<Row> lexicographicComparator(List<Key> keys) {
        return (a, b) -> {
            for (int ki = 0; ki < keys.size(); ki++) {
                Key key = keys.get(ki);
                int c = compareByKey(key, a.get(key.field()), b.get(key.field()));
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        };
    }

    /**
     * 单个键上的比较：{@code null} 恒在前（不受升降序影响），其余按值比较后按方向取反。
     */
    static int compareByKey(Key key, Object a, Object b) {
        int c = Cmp.compare(a, b);
        if (a == null || b == null) {
            return c;
        }
        return key.ascending() ? c : -c;
    }

    private MultiSort() {
    }
}
