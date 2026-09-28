package sortstable;

/**
 * 比较计数器（固定入口，勿改）。
 *
 * <p>本库的规模判据就是这里的 {@link #count}：它统计**键比较次数**，与墙钟、机器速度、
 * 线程调度都无关。约定是：每一次键比较都必须经过 {@link #compare(Object, Object)}，
 * 每调用一次 {@code count} 自增 1。
 */
public final class Cmp {

    /** 已发生的键比较次数。 */
    public static volatile long count;

    /** 把计数器归零。 */
    public static void reset() {
        count = 0L;
    }

    /**
     * 比较两个键值，并按约定把 {@link #count} 加一。
     *
     * <p>{@code null} 排在非 {@code null} **之前**：任一方为 {@code null} 时返回值的正负号
     * 只由「是不是 null」决定（{@code null} 在前），与升序 / 降序无关 —— 升降序要不要取反
     * 由调用方负责，见 {@link MultiSort}。
     *
     * <p>两个都非 {@code null} 时：{@code Long} 按数值比较，{@code String} 按
     * {@link String#compareTo}（码点序，区域无关）比较。类型不一致抛
     * {@link IllegalArgumentException}。
     */
    public static int compare(Object a, Object b) {
        count++;
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Long && b instanceof Long) {
            return Long.compare((Long) a, (Long) b);
        }
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        throw new IllegalArgumentException("键值类型不一致：" + a.getClass().getName()
                + " vs " + b.getClass().getName());
    }

    private Cmp() {
    }
}
