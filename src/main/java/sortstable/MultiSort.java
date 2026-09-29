package sortstable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多键稳定排序。
 *
 * <p>只做**一趟**稳定排序（JDK 的 TimSort，{@link List#sort(Comparator)}），
 * 比较器按 {@code keys} 的优先级逐键判定：第一个能分出大小的键决定次序，
 * 前面的键都相等才看下一个键。这样比较次数是 O(n log n)，
 * 且大多数比较在首键就分出结果，**不随键数线性增长**。
 *
 * <p>稳定性由 TimSort 保证：所有键都相等的两行不会交换，保持输入顺序。
 * 每次键值比较都经过 {@link Cmp#compare(Object, Object)}，计数口径不变。
 */
public final class MultiSort {

    /**
     * 按 {@code keys} 给出的优先级排序 {@code rows}（{@code keys[0]} 优先级最高），
     * 返回新列表；不修改调用方传入的 {@code rows}。
     */
    public static List<Row> sort(List<Row> rows, List<Key> keys) {
        List<Row> out = new ArrayList<>(rows);
        if (keys.isEmpty() || out.size() < 2) {
            return out;
        }
        out.sort(comparator(keys));
        return out;
    }

    /**
     * 多键复合比较器：按优先级逐键比较，首个非零结果即最终次序；
     * 所有键都相等时返回 0（排序稳定，保持输入顺序）。
     */
    static Comparator<Row> comparator(List<Key> keys) {
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
