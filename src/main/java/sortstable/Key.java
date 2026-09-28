package sortstable;

/** 一个排序键：字段名 + 升序 / 降序。 */
public final class Key {

    private final String field;
    private final boolean ascending;

    public Key(String field, boolean ascending) {
        if (field == null) {
            throw new IllegalArgumentException("field 不能为 null");
        }
        this.field = field;
        this.ascending = ascending;
    }

    public String field() {
        return field;
    }

    /** {@code true} 为升序，{@code false} 为降序。 */
    public boolean ascending() {
        return ascending;
    }

    @Override
    public String toString() {
        return field + (ascending ? " asc" : " desc");
    }
}
