package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;

import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * 모집 상세(RecruitDetail) 화면의 Firestore "쓰기" 호출 전담.
 *
 * [부분 리팩토링 — TeamDetailRepository 와 동일한 원칙]
 *   화면마다 Activity 와 분리했을 때 가장 깔끔해지는 Firestore 경계를 분리한다.
 *   이 화면은 신청/재신청이 자격 검증을 포함한 단일 트랜잭션이라
 *   그 트랜잭션 전체를 분리하는 것이 가장 깔끔하다.
 *   읽기(loadRecruitAndMyInfo 등)는 UI 바인딩과 1:1 이라 Activity 잔류.
 *
 * [변경 전] RecruitDetailActivity.applyOrReapply() 가
 *   db.runTransaction 을 직접 호출 — 자격 검증 + 신청 문서 기록 + 내 신청 목록 기록.
 *
 * [변경 후] 트랜잭션이 이 Repository 로 이동.
 *   Activity 는 WriteCallback 으로 결과만 받아 토스트/버튼 상태를 갱신한다.
 *   자격 검증 메시지·기록 필드·merge 옵션은 변경 전과 동일.
 */
public class RecruitDetailRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 / 파라미터 ──────────────────────────────────────────────────────────

    /** 쓰기 결과 콜백 — UI 갱신(토스트, 버튼 상태)은 Activity 책임 */
    public interface WriteCallback {
        void onSuccess();
        void onFailure(@NonNull Exception e);
    }

    /** 신청자 정보 묶음 — Activity 필드를 한 번에 전달 */
    public static class ApplicantData {
        public final String currentUid;
        public final String myTeamId;
        public final String myTeamName;
        public final String myNickname;
        public final int    mySkill;

        public ApplicantData(String currentUid, String myTeamId, String myTeamName,
                             String myNickname, int mySkill) {
            this.currentUid = currentUid;
            this.myTeamId   = myTeamId;
            this.myTeamName = myTeamName;
            this.myNickname = myNickname;
            this.mySkill    = mySkill;
        }
    }

    // ── 신청 / 재신청 (단일 트랜잭션) ────────────────────────────────────────────

    /**
     * 모집글 신청 또는 재신청(거절 → 대기).
     *
     * 트랜잭션 내부 (변경 전과 동일):
     *   1) recruitPosts/{id} 조회, 없으면 레거시 recruits/{id} 폴백
     *   2) 자격 검증 — 실패 시 IllegalStateException 으로 중단
     *      · 본인이 올린 글  → "본인이 올린 글에는 신청할 수 없습니다."
     *      · 내 팀이 올린 글 → "내 팀이 올린 글에는 신청할 수 없습니다."
     *      · 정식선수 모집 + 소속팀 있음 → "현재 소속된 팀이 있습니다."
     *      · 이미 신청(거절 아님)        → "이미 신청한 글입니다."
     *   3) {post}/applicants/{uid} 에 pending 기록 (set + merge — 재신청 겸용)
     *   4) profiles/{uid}/applications/{recruitId} 에도 기록
     *      → 인덱스 없이 내 신청 목록 조회 가능
     */
    public void applyOrReapply(@NonNull String recruitId,
                               @NonNull ApplicantData a,
                               @NonNull WriteCallback callback) {

        db.runTransaction(tr -> {
                    // 1) 본문(신규/레거시) 결정
                    DocumentReference postRef = db.collection("recruitPosts").document(recruitId);
                    DocumentSnapshot postSnap = tr.get(postRef);
                    if (!postSnap.exists()) {
                        postRef = db.collection("recruits").document(recruitId);
                        postSnap = tr.get(postRef);
                        if (!postSnap.exists()) throw new IllegalStateException("삭제된 글입니다.");
                    }

                    DocumentSnapshot profSnap = tr.get(
                            db.collection("profiles").document(a.currentUid));

                    String myTeamIdTx   = AppUtils.safe(profSnap.getString("myTeam"));
                    String postTeamIdTx = AppUtils.safe(postSnap.getString("teamId"));
                    String typeTx       = AppUtils.normalizeRecruitType(postSnap.getString("recruitType"));
                    String authorTx     = AppUtils.firstNonEmpty(
                            postSnap.getString("authorUid"),
                            postSnap.getString("writerUid"),
                            postSnap.getString("uid"));

                    // 2) 자격 검증
                    if (!AppUtils.isEmpty(authorTx) && authorTx.equals(a.currentUid))
                        throw new IllegalStateException("본인이 올린 글에는 신청할 수 없습니다.");
                    if (!AppUtils.isEmpty(myTeamIdTx) && myTeamIdTx.equals(postTeamIdTx))
                        throw new IllegalStateException("내 팀이 올린 글에는 신청할 수 없습니다.");
                    if ("regular".equals(typeTx) && !AppUtils.isEmpty(myTeamIdTx))
                        throw new IllegalStateException("현재 소속된 팀이 있습니다.");

                    DocumentReference apRef  = postRef.collection("applicants").document(a.currentUid);
                    DocumentSnapshot  apSnap = tr.get(apRef);

                    long now = System.currentTimeMillis();
                    if (apSnap.exists()) {
                        String st = AppUtils.safe(apSnap.getString("status")).toLowerCase();
                        if (!st.startsWith("rej")) throw new IllegalStateException("이미 신청한 글입니다.");
                    }

                    // 3) 신청 문서 기록 (재신청이면 같은 문서를 pending 으로 덮어씀)
                    Map<String, Object> apData = new HashMap<>();
                    apData.put("status",          "pending");
                    apData.put("timestamp",       now);
                    apData.put("teamId",          a.myTeamId);
                    apData.put("teamName",        a.myTeamName);
                    apData.put("nickname",        a.myNickname);
                    apData.put("skill",           a.mySkill);
                    apData.put("applicantUserId", a.currentUid);
                    tr.set(apRef, apData, SetOptions.merge());

                    // 4) profiles/{uid}/applications 에도 저장 → 인덱스 없이 내 신청 조회 가능
                    DocumentReference myAppRef = db.collection("profiles")
                            .document(a.currentUid)
                            .collection("applications")
                            .document(recruitId);
                    Map<String, Object> myApp = new HashMap<>();
                    myApp.put("postId",    recruitId);
                    myApp.put("postType",  "recruit");
                    myApp.put("status",    "pending");
                    myApp.put("timestamp", now);
                    tr.set(myAppRef, myApp, SetOptions.merge());

                    return null;

                }).addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }
}