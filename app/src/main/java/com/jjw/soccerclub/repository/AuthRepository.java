package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * 인증(users / profiles) 관련 Firebase 호출 전담.
 *
 * LoginActivity, RegisterActivity 가 공유한다.
 *
 * [설계 기준 — 프로젝트 공통 규칙]
 *   리스트 화면  : ViewModel + LiveData (화면 회전 시 상태 유지 필요)
 *   상세/작성/인증: Repository 콜백 분리만 적용
 *     → 인증 화면은 회전 시 유지할 상태가 없고 일회성 요청만 있으므로
 *       ViewModel 을 두면 오히려 과설계. Firestore/Auth 코드 격리가 목적.
 *
 * 로그인 흐름 (3단계):
 *   1) users 컬렉션에서 username → email 조회
 *   2) Firebase Auth 이메일/비밀번호 로그인
 *   3) profiles/{uid} 존재 여부 확인 → 홈 / 프로필 생성 분기
 *
 * 회원가입 흐름 (3단계):
 *   1) users 컬렉션에서 username 중복 체크
 *   2) Firebase Auth 계정 생성
 *   3) users/{uid} 에 username, email 저장
 */
public class AuthRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ═════════════════════════════════════════════════════════════════════════════
    // 로그인
    // ═════════════════════════════════════════════════════════════════════════════

    /** 로그인 실패 사유. Activity 는 이 값으로 사용자 안내 문구만 결정한다. */
    public enum LoginError {
        USERNAME_NOT_FOUND,   // 1단계 실패: 아이디 없음
        WRONG_PASSWORD,       // 2단계 실패: 비밀번호 불일치
        PROFILE_CHECK_FAILED, // 3단계 실패: 프로필 조회 오류
        NETWORK               // 그 외 네트워크/서버 오류
    }

    /** 로그인 결과 콜백 */
    public interface LoginCallback {
        /**
         * @param uid        로그인된 사용자 UID
         * @param hasProfile profiles/{uid} 문서 존재 여부
         *                   true  → 홈으로 이동
         *                   false → 프로필 생성 화면으로 이동
         */
        void onSuccess(@NonNull String uid, boolean hasProfile);

        void onError(@NonNull LoginError error);
    }

    /**
     * username + password 로그인.
     * Activity 는 결과 콜백만 받아 화면 전환/토스트를 처리한다.
     */
    public void login(@NonNull String username,
                      @NonNull String password,
                      @NonNull LoginCallback callback) {

        // 1단계: username → email 조회
        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        callback.onError(LoginError.USERNAME_NOT_FOUND);
                        return;
                    }

                    String email = querySnapshot.getDocuments().get(0).getString("email");
                    if (email == null || email.isEmpty()) {
                        callback.onError(LoginError.USERNAME_NOT_FOUND);
                        return;
                    }

                    // 2단계: Firebase Auth 로그인
                    auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(task -> {
                                if (!task.isSuccessful()) {
                                    callback.onError(LoginError.WRONG_PASSWORD);
                                    return;
                                }

                                FirebaseUser user = auth.getCurrentUser();
                                if (user == null) {
                                    callback.onError(LoginError.WRONG_PASSWORD);
                                    return;
                                }

                                // 3단계: UID 로 프로필 존재 여부 확인
                                checkProfileExists(user.getUid(), callback);
                            });
                })
                .addOnFailureListener(e -> callback.onError(LoginError.NETWORK));
    }

    /** profiles/{uid} 존재 여부 확인 후 onSuccess 로 결과 전달 */
    private void checkProfileExists(@NonNull String uid,
                                    @NonNull LoginCallback callback) {
        db.collection("profiles")
                .document(uid)
                .get()
                .addOnSuccessListener(doc ->
                        callback.onSuccess(uid, doc.exists()))
                .addOnFailureListener(e ->
                        callback.onError(LoginError.PROFILE_CHECK_FAILED));
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // 회원가입
    // ═════════════════════════════════════════════════════════════════════════════

    /** 회원가입 실패 사유 */
    public enum RegisterError {
        USERNAME_TAKEN,         // 1단계: 이미 사용 중인 username
        DUPLICATE_CHECK_FAILED, // 1단계: 중복 확인 쿼리 실패
        EMAIL_TAKEN,            // 2단계: 이미 사용 중인 이메일
        ACCOUNT_CREATE_FAILED,  // 2단계: 그 외 계정 생성 실패
        SAVE_FAILED             // 3단계: users/{uid} 저장 실패
    }

    /** 회원가입 결과 콜백 */
    public interface RegisterCallback {
        void onSuccess();

        /**
         * @param error  실패 사유
         * @param detail 원인 예외 메시지 (없으면 null) — 기존 토스트 문구의
         *               "...: e.getMessage()" 부분을 그대로 재현하기 위해 전달
         */
        void onError(@NonNull RegisterError error, @Nullable String detail);
    }

    /**
     * username 중복 체크 → Auth 계정 생성 → users/{uid} 저장.
     * 입력값 검증(빈 값, 이메일 형식, 비밀번호 길이)은 Activity 책임.
     */
    public void register(@NonNull String username,
                         @NonNull String email,
                         @NonNull String password,
                         @NonNull RegisterCallback callback) {

        // 1단계: username 중복 체크
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        callback.onError(RegisterError.USERNAME_TAKEN, null);
                        return;
                    }
                    createAccount(username, email, password, callback);
                })
                .addOnFailureListener(e ->
                        callback.onError(RegisterError.DUPLICATE_CHECK_FAILED, e.getMessage()));
    }

    /** 2단계: Firebase Auth 계정 생성 */
    private void createAccount(@NonNull String username,
                               @NonNull String email,
                               @NonNull String password,
                               @NonNull RegisterCallback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) {
                        callback.onError(RegisterError.ACCOUNT_CREATE_FAILED, null);
                        return;
                    }
                    saveUser(user.getUid(), username, email, callback);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        callback.onError(RegisterError.EMAIL_TAKEN, null);
                    } else {
                        callback.onError(RegisterError.ACCOUNT_CREATE_FAILED, e.getMessage());
                    }
                });
    }

    /** 3단계: users/{uid} 에 username, email 저장 */
    private void saveUser(@NonNull String uid,
                          @NonNull String username,
                          @NonNull String email,
                          @NonNull RegisterCallback callback) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("email", email);

        db.collection("users").document(uid)
                .set(userMap)
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e ->
                        callback.onError(RegisterError.SAVE_FAILED, e.getMessage()));
    }
}