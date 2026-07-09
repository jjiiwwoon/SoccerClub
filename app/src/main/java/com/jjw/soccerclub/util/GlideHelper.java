package com.jjw.soccerclub.util;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.jjw.soccerclub.R;

/**
 * Glide 공통 래퍼
 * - 프로젝트 전역에서 반복되는 Glide 호출 패턴을 하나로 통합
 */
public class GlideHelper {

    private GlideHelper() {}

    /**
     * 일반 이미지 로드 (라운드 코너)
     * placeholder: ic_shield_gray
     */
    public static void loadImage(Context context, String url, ImageView target) {
        Glide.with(context)
                .load(AppUtils.isEmpty(url) ? null : url)
                .placeholder(R.drawable.ic_shield_gray)
                .error(R.drawable.ic_shield_gray)
                .into(target);
    }

    /**
     * 원형 프로필 이미지 로드
     * placeholder: ic_person_placeholder
     */
    public static void loadProfile(Context context, String url, ImageView target) {
        Glide.with(context)
                .load(AppUtils.isEmpty(url) ? null : url)
                .circleCrop()
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .into(target);
    }

    /**
     * 팀 로고 로드 (라운드 코너)
     * placeholder: default_profile_image
     */
    public static void loadTeamLogo(Context context, String url, ImageView target) {
        Glide.with(context)
                .load(AppUtils.isEmpty(url) ? null : url)
                .centerCrop()
                .placeholder(R.drawable.default_profile_image)
                .error(R.drawable.default_profile_image)
                .into(target);
    }

    /**
     * 라운드 코너 이미지 로드 (radius 지정)
     * placeholder: empty_player
     */
    public static void loadRounded(Context context, String url, ImageView target, int radiusDp) {
        int radiusPx = dpToPx(context, radiusDp);
        Glide.with(context)
                .load(AppUtils.isEmpty(url) ? null : url)
                .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                .placeholder(R.drawable.empty_player)
                .error(R.drawable.empty_player)
                .into(target);
    }

    /**
     * 리소스 ID로 라운드 코너 이미지 로드
     */
    public static void loadRoundedRes(Context context, int resId, ImageView target, int radiusDp) {
        int radiusPx = dpToPx(context, radiusDp);
        Glide.with(context)
                .load(resId)
                .transform(new CenterCrop(), new RoundedCorners(radiusPx))
                .into(target);
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}