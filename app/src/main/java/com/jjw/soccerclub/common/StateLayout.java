package com.jjw.soccerclub.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.jjw.soccerclub.R;

public class StateLayout extends FrameLayout {

    public enum State { LOADING, CONTENT, EMPTY }

    private @LayoutRes int loadingLayoutRes = R.layout.view_state_loading;
    private @LayoutRes int emptyLayoutRes   = R.layout.view_state_empty;

    private View loadingView;
    private View emptyView;
    private View contentView;

    private State current = State.CONTENT;

    public StateLayout(Context context) {
        super(context);
        init(null);
    }

    public StateLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public StateLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StateLayout);
            int ll = a.getResourceId(R.styleable.StateLayout_loadingLayout, 0);
            int el = a.getResourceId(R.styleable.StateLayout_emptyLayout, 0);
            if (ll != 0) loadingLayoutRes = ll;
            if (el != 0) emptyLayoutRes = el;
            a.recycle();
        }
        post(this::ensureInflated);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureInflated();
    }

    private void ensureInflated() {
        if (contentView == null && getChildCount() > 0) {
            contentView = getChildAt(0);
        }
        if (loadingView == null) {
            loadingView = LayoutInflater.from(getContext()).inflate(loadingLayoutRes, this, false);
            addView(loadingView);
        }
        if (emptyView == null) {
            emptyView = LayoutInflater.from(getContext()).inflate(emptyLayoutRes, this, false);
            addView(emptyView);
        }
        applyState(false);
    }

    // ── 상태 전환 ─────────────────────────────────────────────────────────────────
    public void showLoading() { current = State.LOADING; applyState(true); }
    public void showContent() { current = State.CONTENT; applyState(true); }
    public void showEmpty()   { current = State.EMPTY;   applyState(true); }

    private void applyState(boolean animate) {
        if (loadingView == null || emptyView == null || contentView == null) return;

        View toShow, toHide1, toHide2;
        switch (current) {
            case LOADING:
                toShow = loadingView; toHide1 = contentView; toHide2 = emptyView; break;
            case EMPTY:
                toShow = emptyView;   toHide1 = contentView; toHide2 = loadingView; break;
            default:
                toShow = contentView; toHide1 = loadingView; toHide2 = emptyView; break;
        }

        if (animate) {
            toShow.setAlpha(0f);
            toShow.setVisibility(VISIBLE);
            toShow.animate().alpha(1f).setDuration(150).start();
            toHide1.setVisibility(GONE);
            toHide2.setVisibility(GONE);
        } else {
            toShow.setVisibility(VISIBLE);
            toHide1.setVisibility(GONE);
            toHide2.setVisibility(GONE);
        }
    }

    // ── 빈 화면 커스터마이징 ──────────────────────────────────────────────────────

    /** 메인 메시지 설정 */
    public void setEmptyMessage(CharSequence msg) {
        if (emptyView == null) return;
        View tv = emptyView.findViewById(R.id.txtEmptyMessage);
        if (tv instanceof TextView) ((TextView) tv).setText(msg);
    }

    /** ✅ 서브 메시지 설정 (없으면 숨김) */
    public void setEmptySubMessage(CharSequence msg) {
        if (emptyView == null) return;
        TextView tv = emptyView.findViewById(R.id.txtEmptySubMessage);
        if (tv == null) return;
        if (msg == null || msg.length() == 0) {
            tv.setVisibility(GONE);
        } else {
            tv.setText(msg);
            tv.setVisibility(VISIBLE);
        }
    }

    /** ✅ 행동 유도 버튼 설정 (없으면 숨김) */
    public void setEmptyAction(CharSequence label, OnClickListener listener) {
        if (emptyView == null) return;
        Button btn = emptyView.findViewById(R.id.btnEmptyAction);
        if (btn == null) return;
        if (label == null || listener == null) {
            btn.setVisibility(GONE);
        } else {
            btn.setText(label);
            btn.setOnClickListener(listener);
            btn.setVisibility(VISIBLE);
        }
    }

    /** ✅ 아이콘 변경 */
    public void setEmptyIcon(@DrawableRes int iconRes) {
        if (emptyView == null) return;
        ImageView img = emptyView.findViewById(R.id.imgEmptyIcon);
        if (img == null) return;
        if (iconRes == 0) {
            img.setVisibility(GONE);
        } else {
            img.setImageResource(iconRes);
            img.setVisibility(VISIBLE);
        }
    }
}