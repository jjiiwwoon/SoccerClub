package com.example.soccerclub.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.example.soccerclub.R;

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

    public void setEmptyMessage(CharSequence msg) {
        if (emptyView == null) return;
        View tv = emptyView.findViewById(R.id.txtEmptyMessage);
        if (tv instanceof TextView) {
            ((TextView) tv).setText(msg);
        }
    }
}