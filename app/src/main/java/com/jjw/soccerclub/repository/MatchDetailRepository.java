package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * 시합 상세(MatchDetail) 화면의 Firestore "쓰기" 호출 전담.
 *
 * [부분 리팩토링 — TeamDetailRepository 와 동일한 원칙]
 *   화면마다 Activity 와 분리했을 때 가장 깔끔해지는 Firestore 경계를 분리한다.
 *   - TeamDetail  : 읽기 쿼리가 복잡 → 읽기만 분리
 *   - MatchDetail : 신청 시 3개 문서(applicants / lastApplicantTs /
 *                   teams.matchApplications)를 묶어 쓰는 쓰기가 복잡 → 쓰기만 분리
 *   읽기(loadMatchDetail 등)는 단건 조회 + UI 바인딩이 1:1 이라 Activity 잔류.
 *
 * [변경 전] MatchDetailActivity.submitApplication() / reapply() 가
 *   FirebaseFirestore.getInstance() 를 직접 호출해 3개 문서를 순차 기록.
 *
 * [변경 후] 이 Repository 가 쓰기를 담당하고,
 *   Activity 는 WriteCallback 으로 결과만 받아 토스트/버튼 상태를 갱신한다.
 *   기록되는 필드/순서/타이밍은 변경 전과 동일.
 */
public class MatchDetailRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 / 파라미터 ──────────────────────────────────────────────────────────

    /** 쓰기 결과 콜백 — UI 갱신(토스트, 버튼 상태)은 Activity 책임 */
    public interface WriteCallback {
        void onSuccess();
        void onFailure(@NonNull Exception e);
    }

    /** 신청자 정보 묶음 — Activity 필드 6개를 한 번에 전달 */
    public static class ApplicantData {
        public final String userId;
        public final String nickname;
        public final int    skill;
        public final String teamId;
        public final String teamName;
        public final String teamLogoUrl;

        public ApplicantData(String userId, String nickname, int skill,
                             String teamId, String teamName, String teamLogoUrl) {
            this.userId      = userId;
            this.nickname    = nickname;
            this.skill       = skill;
            this.teamId      = teamId;
            this.teamName    = teamName;
            this.teamLogoUrl = teamLogoUrl;
        }
    }

    // ── 최초 신청 ────────────────────────────────────────────────────────────────

    /**
     * 시합 최초 신청.
     *
     * 기록 순서 (변경 전과 동일):
     *   1) matches/{matchId}/applicants/{uid} 생성 (status=pending)
     *   2) matches/{matchId}.lastApplicantTs 갱신 → 상단 NEW 뱃지 트리거
     *   3) teams/{myTeamId}/matchApplications/{matchId} 생성
     *      → 팀 관점에서 신청한 시합 목록 조회 가능 (인덱스 불필요)
     *
     * 2)·3) 은 변경 전 코드와 동일하게 fire-and-forget — 1) 성공 시점에
     * onSuccess 를 호출한다 (기존 토스트 타이밍 유지).
     */
    public void submitApplication(@NonNull String matchId,
                                  @NonNull ApplicantData applicant,
                                  @NonNull WriteCallback callback) {
        long now = System.currentTimeMillis();

        Map<String, Object> data = new HashMap<>();
        data.put("userId",      applicant.userId);
        data.put("nickname",    applicant.nickname);
        data.put("skill",       applicant.skill);
        data.put("timestamp",   now);
        data.put("teamName",    applicant.teamName);
        data.put("teamId",      applicant.teamId);
        data.put("teamLogoUrl", applicant.teamLogoUrl);
        data.put("status",      "pending");
        data.put("responded",   false);

        db.collection("matches").document(matchId)
                .collection("applicants").document(applicant.userId)
                .set(data)
                .addOnSuccessListener(v -> {
                    db.collection("matches").document(matchId)
                            .update("lastApplicantTs", now);

                    Map<String, Object> teamApp = new HashMap<>();
                    teamApp.put("matchId",      matchId);
                    teamApp.put("postType",     "match");
                    teamApp.put("status",       "pending");
                    teamApp.put("timestamp",    now);
                    teamApp.put("applicantUid", applicant.userId); // 신청한 주장 uid
                    db.collection("teams").document(applicant.teamId)
                            .collection("matchApplications").document(matchId)
                            .set(teamApp);

                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onFailure);
    }

    // ── 재신청 (거절 → 대기) ─────────────────────────────────────────────────────

    /**
     * 거절당한 신청을 다시 pending 으로 되돌린다.
     *
     * 기록 순서 (변경 전과 동일):
     *   1) matches/{matchId}/applicants/{uid} 업데이트
     *      (status=pending, responded=false, reapplyCount +1)
     *   2) matches/{matchId}.lastApplicantTs 갱신
     *   3) teams/{myTeamId}/matchApplications/{matchId} 상태 merge 갱신
     */
    public void reapply(@NonNull String matchId,
                        @NonNull String currentUid,
                        @NonNull String myTeamId,
                        @NonNull WriteCallback callback) {
        long now = System.currentTimeMillis();

        Map<String, Object> up = new HashMap<>();
        up.put("status",       "pending");
        up.put("timestamp",    now);
        up.put("responded",    false);
        up.put("reapplyCount", FieldValue.increment(1));

        db.collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .update(up)
                .addOnSuccessListener(v -> {
                    db.collection("matches").document(matchId)
                            .update("lastApplicantTs", now);

                    Map<String, Object> teamApp = new HashMap<>();
                    teamApp.put("status",    "pending");
                    teamApp.put("timestamp", now);
                    db.collection("teams").document(myTeamId)
                            .collection("matchApplications").document(matchId)
                            .set(teamApp, SetOptions.merge());

                    callback.onSuccess();
                })
                .addOnFailureListener(callback::onFailure);
    }
}