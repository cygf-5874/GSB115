package sortstable;

import java.util.ArrayList;
import java.util.List;

/**
 * 多键稳定排序。
 *
 * <p>当前实现把**每个键拆成独立的一趟排序**：从优先级最低的键开始，逐键用一趟稳定的
 * 「冒泡」把整表顺一遍，靠稳定性把前面的结果套在后面结果之内。语义是对的
 * （稳定、{@code null} 在前、不改入参），但每趟都要把整表来回扫很多遍，
 * 比较次数是 **键数 × O(n²)**。
 */
public final class MultiSort {

    /**
     * 按 {@code keys} 给出的优先级排序 {@code rows}（{@code keys[0]}`} 优先级最高），
     * 返回新列表；不修改调用方传入的 {@code rows}。
     */
    public static List<Row> sort(List<Row> rows, List<Key> keys) {
        List<Row> out = new ArrayList<>(rows);
        for (int ki = keys.size() - 1; ki >= 0; ki--) {
            stableBubblePass(out, keys.get(ki));
        }
        return out;
    }

    /** 按单个键做一趟稳定冒泡：只有严格大于才交换，相等不交换，所以稳定。 */
    private static void stableBubblePass(List<Row> rows, Key key) {
        int n = rows.size();
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - 1 - i; j++) {
                Row a = rows.get(j);
                Row b = rows.get(j + 1);
                if (compareByKey(key, a.get(key.field()), b.get(key.field())) > 0) {
                    rows.set(j, b);
                    rows.set(j + 1, a);
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
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
