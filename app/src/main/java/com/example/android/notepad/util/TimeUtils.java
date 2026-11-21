package com.example.android.notepad.util;

import android.content.Context;

import com.example.android.notepad.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 时间展示工具：列表用相对时间，编辑器用完整时间，待办用日期。
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private static final SimpleDateFormat FULL = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final SimpleDateFormat MD = new SimpleDateFormat("M月d日", Locale.CHINA);
    private static final SimpleDateFormat YMD = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);

    /** 列表时间戳：刚刚 / N 分钟前 / 今天 HH:mm / 昨天 HH:mm / M月d日 / yyyy年M月d日 */
    public static String formatListTime(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff >= 0 && diff < TimeUnit.MINUTES.toMillis(1)) {
            return "刚刚";
        }
        if (diff >= 0 && diff < TimeUnit.HOURS.toMillis(1)) {
            return (diff / TimeUnit.MINUTES.toMillis(1)) + " 分钟前";
        }

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();

        if (isSameDay(target, today)) {
            return "今天 " + HM.format(new Date(timestamp));
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(target, yesterday)) {
            return "昨天 " + HM.format(new Date(timestamp));
        }
        if (target.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            return MD.format(new Date(timestamp));
        }
        return YMD.format(new Date(timestamp));
    }

    public static String formatFull(long timestamp) {
        return timestamp <= 0 ? "" : FULL.format(new Date(timestamp));
    }

    public static String formatDate(long timestamp) {
        return timestamp <= 0 ? "" : YMD.format(new Date(timestamp));
    }

    /** 毫秒 → mm:ss（超过 1 小时显示 h:mm:ss） */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.CHINA, "%02d:%02d", minutes, seconds);
    }

    /**
     * 待办截止文案：今天截止 / 明天截止 / M月d日 截止，过期时加「已逾期 ·」前缀。
     */
    public static String formatDue(Context context, long dueAt) {
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(dueAt);
        Calendar today = Calendar.getInstance();

        boolean overdue = startOfDay(due) < startOfDay(today);
        int dayDiff = daysBetween(today, due);

        String base;
        if (dayDiff == 0) {
            base = context.getString(R.string.todo_due_today);
        } else if (dayDiff == 1) {
            base = context.getString(R.string.todo_due_tomorrow);
        } else if (due.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            base = context.getString(R.string.todo_due_date, MD.format(due.getTime()));
        } else {
            base = context.getString(R.string.todo_due_date, YMD.format(due.getTime()));
        }

        return overdue ? context.getString(R.string.todo_due_overdue, base) : base;
    }

    private static long startOfDay(Calendar c) {
        Calendar copy = (Calendar) c.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy.getTimeInMillis();
    }

    private static int daysBetween(Calendar from, Calendar to) {
        long diff = startOfDay(to) - startOfDay(from);
        return (int) Math.round(diff / (double) TimeUnit.DAYS.toMillis(1));
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static long endOfDay(int year, int month, int dayOfMonth) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, dayOfMonth, 23, 59, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }
}
