import sortstable.Cmp;
import sortstable.Key;
import sortstable.MultiSort;
import sortstable.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复现脚本：把当前的比较次数规模摆出来。
 *
 * <p>用法：{@code java -cp out Repro [n] [keys]}（默认 n=2000、keys=2）。
 * 它只打印「行数 / 键数 / Cmp.count」，不做任何断言 —— 用来对抗下面这条经验：
 * 十万元素的多键排序要跑四秒多，其中大部分时间花在重复比较上。
 *
 * <p>注意：它跑的是**当前实现**。n 调大以后耗时会迅速离谱，所以默认给的是小规模。
 */
public final class Repro {

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 2000;
        int keyCount = args.length > 1 ? Integer.parseInt(args[1]) : 2;

        List<Row> rows = new ArrayList<>(n);
        long x = 0x2545F4914F6CDD1DL;
        for (int i = 0; i < n; i++) {
            Map<String, Object> f = new LinkedHashMap<>();
            x = x * 6364136223846793005L + 1442695040888963407L;
            f.put("k0", (long) ((x >>> 24) % 1_000_000L));
            for (int j = 1; j < keyCount; j++) {
                x = x * 6364136223846793005L + 1442695040888963407L;
                f.put("k" + j, "s" + ((x >>> 24) % 4096L));
            }
            rows.add(new Row("r" + i, f));
        }
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < keyCount; i++) {
            keys.add(new Key("k" + i, i % 2 == 0));
        }

        Cmp.reset();
        long t0 = System.nanoTime();
        List<Row> sorted = MultiSort.sort(rows, keys);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        System.out.println("行数=" + n + " 键数=" + keyCount
                + " Cmp.count=" + Cmp.count + " 耗时=" + ms + " ms 输出行数=" + sorted.size());
    }

    private Repro() {
    }
}
