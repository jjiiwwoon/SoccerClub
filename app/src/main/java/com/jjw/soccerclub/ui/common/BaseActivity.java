package com.jjw.soccerclub.ui.common;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 시스템 바(상태바 + 네비게이션 바) 인셋을 자동으로 처리하는 Base Activity.
 *
 * targetSdk 35에서는 엣지-투-엣지가 강제 적용되므로,
 * 모든 Activity가 이 클래스를 상속하면 콘텐츠가 시스템 바와 겹치지 않습니다.
 *
 * 직접 인셋을 관리하는 Activity(ChatRoomActivity 등)는
 * skipSystemBarInsets()를 오버라이드하여 true를 반환하면 됩니다.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        if (!skipSystemBarInsets()) {
            applySystemBarInsets();
        }
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!skipSystemBarInsets()) {
            applySystemBarInsets();
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        if (!skipSystemBarInsets()) {
            applySystemBarInsets();
        }
    }

    /**
     * 시스템 바 인셋 자동 처리를 건너뛸지 여부.
     * ChatRoomActivity 등 직접 인셋을 관리하는 Activity에서 true 반환.
     */
    protected boolean skipSystemBarInsets() {
        return false;
    }

    /**
     * android.R.id.content (FrameLayout) 에 시스템 바 패딩 적용.
     * 상태바(top) + 네비게이션 바(bottom)만큼 패딩을 줘서
     * 콘텐츠가 시스템 UI와 겹치지 않도록 함.
     */
    private void applySystemBarInsets() {
        View contentView = findViewById(android.R.id.content);
        if (contentView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });
    }
}