package cn.firedance.hearth.auth;

/**
 * 数据范围。
 *
 * <p>只有两种：
 * <ul>
 *   <li>{@code all = true} —— 老板，不加过滤</li>
 *   <li>{@code all = false} —— 老师，只看 {@code staffId} 名下的记录</li>
 * </ul>
 *
 * <p>Mapper 里用参数化条件，不要动态拼 SQL：
 * <pre>
 * WHERE (#{scope.all} = 1 OR staff_id = #{scope.staffId})
 * </pre>
 * 拼 SQL 最容易在某个分支漏掉条件，一旦漏了就是串数据。
 */
public record DataScope(boolean all, Long staffId) {

    public static DataScope allScope() {
        return new DataScope(true, null);
    }

    public static DataScope self(Long staffId) {
        return new DataScope(false, staffId);
    }

    public boolean isSelfOnly() {
        return !all;
    }
}
