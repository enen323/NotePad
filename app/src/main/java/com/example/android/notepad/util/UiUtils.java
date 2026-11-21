package com.example.android.notepad.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 全面屏（edge-to-edge）适配：targetSdk 35+ 默认贴边绘制，
 * 这里把系统栏高度补成根布局内边距，避免标题栏/底部导航被状态栏遮挡。
 */
public final class UiUtils {

    private UiUtils() {
    }

    public static void applySystemBarsInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** 编辑页需要额外把输入法高度让出来，否则光标会被键盘挡住。 */
    public static void applyInsetsWithIme(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }
}
