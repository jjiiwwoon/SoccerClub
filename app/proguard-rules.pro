# ═══════════════════════════════════════════════════════════════════════════════
# SoccerClub ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════════════════════
#
# release 빌드에서 isMinifyEnabled = true 로 R8 을 활성화했을 때
# 런타임 크래시를 방지하기 위한 keep 규칙.
#
# 규칙 추가 기준:
#   리플렉션·직렬화를 사용하는 라이브러리만 명시적 keep 이 필요하다.
#   AndroidX / Material 등은 자체 consumer-rules.pro 를 번들하므로 추가 불필요.

# ── 디버깅용 (스택트레이스 가독성) ─────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase Firestore ─────────────────────────────────────────────────────────
# toObject(Class) / toObject(Class, ServerTimestampBehavior) 가
# 리플렉션으로 모델 클래스를 인스턴스화한다.
# model 패키지의 모든 POJO 필드/생성자를 보존.
-keep class com.jjw.soccerclub.model.** {
    *;
}
# Firestore 내부 직렬화
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# ── Firebase Auth ──────────────────────────────────────────────────────────────
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ── Firebase Storage ───────────────────────────────────────────────────────────
-keep class com.google.firebase.storage.** { *; }
-dontwarn com.google.firebase.storage.**

# ── Firebase Common / Internal ─────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Google Play Services ───────────────────────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Glide ──────────────────────────────────────────────────────────────────────
# Generated GlideApp / AppGlideModule 보존
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── 네이버 지도 SDK ───────────────────────────────────────────────────────────
-keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**

# ── OkHttp ─────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Shimmer (Facebook) ────────────────────────────────────────────────────────
-keep class com.facebook.shimmer.** { *; }