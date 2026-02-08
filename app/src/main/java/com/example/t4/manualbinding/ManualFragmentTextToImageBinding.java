package com.example.t4.manualbinding;

import android.view.View;
import androidx.annotation.NonNull;

/**
 * 占位手动绑定：已移除对旧布局 id 的引用，避免编译错误。
 * Gradle 会为布局生成真实的 binding，保留此类仅作兼容性占位。
 */
public final class ManualFragmentTextToImageBinding {
    @NonNull
    private final View rootView;

    private ManualFragmentTextToImageBinding(@NonNull View rootView) {
        this.rootView = rootView;
    }

    @NonNull
    public View getRoot() {
        return rootView;
    }

    @NonNull
    public static ManualFragmentTextToImageBinding bind(@NonNull View rootView) {
        return new ManualFragmentTextToImageBinding(rootView);
    }
}
