package sortstable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一行记录：一个唯一标识 {@code id}，加上若干具名字段。
 *
 * <p>字段值只可能是 {@link Long}、{@link String} 或 {@code null}。
 * 取不到的字段一律按 {@code null} 处理。
 */
public final class Row {

    private final String id;
    private final Map<String, Object> fields;

    public Row(String id, Map<String, Object> fields) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为 null");
        }
        if (fields == null) {
            throw new IllegalArgumentException("fields 不能为 null");
        }
        this.id = id;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** 该行的唯一标识；排序稳定性就是靠它肉眼可辨。 */
    public String id() {
        return id;
    }

    /** 取字段值；字段不存在时返回 {@code null}。 */
    public Object get(String field) {
        return fields.get(field);
    }

    @Override
    public String toString() {
        return "Row(" + id + ")";
    }
}
