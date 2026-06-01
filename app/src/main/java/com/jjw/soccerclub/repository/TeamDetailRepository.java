package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;

import java.util.List;

/**
 * 팀 상세(TeamDetail) 화면의 Firestore "읽기" 호출 전담.
 *
 * [부분 리팩토링 — 선택 A]
 *   읽기 쿼리만 Repository 로 분리한다.
 *   가입 신청 / 주장 권한 / 방출 등 "쓰기" 작업과 다이얼로그 흐름은
 *   Activity 책임으로 그대로 남긴다.
 *   → 쓰기/UI 상호작용까지 ViewModel 로 옮기면 오히려 비대해지므로,
 *     읽기 경계만 깔끔히 분리하는 것이 이 화면에는 더 적절.
 *
 * [변경 전] TeamDetailActivity 가 직접 하던 읽기 호출
 *   - getTeamInfo()       : teams/{id} 캐시 + 서버 이중 조회
 *   - loadPlayerList()    : teams/{id}.members → profiles whereIn 조회
 *   - bindRecordSummary() : teamStats/{id} 실시간 리스너
 *
 * [변경 후] 이 Repository 가 Firestore 호출을 담당하고,
 *   Activity 는 콜백으로 결과만 받아 UI 에 바인딩한다.
 */
public class TeamDetailRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    /** 팀 문서 단건 조회 콜백 (캐시/서버 공용) */
    public interface TeamDocCallback {
        void onResult(@Nullable DocumentSnapshot doc);
    }

    /** 내 프로필 조회 콜백 — myTeam 일치 여부 판단용 */
    public interface MyProfileCallback {
        void onResult(@Nullable DocumentSnapshot profile);
    }

    /** 멤버 프로필 목록 조회 콜백 */
    public interface MembersCallback {
        void onResult(@NonNull List<DocumentSnapshot> memberProfiles,
                      @Nullable String captainUID,
                      @Nullable String viceCaptainUID);
        void onEmpty();
        void onError(@NonNull Exception e);
    }

    /** 전적 요약 실시간 콜백 */
    public interface RecordSummaryCallback {
        void onStats(long games, long wins, long draws, long losses, long gf, long ga);
    }

    // ── 내 프로필 (소속 여부 확인) ────────────────────────────────────────────────

    /**
     * 현재 사용자 프로필 1회 조회.
     * Activity 는 결과의 myTeam 필드로 isMyTeam 을 판단한다.
     */
    public void fetchMyProfile(String uid, MyProfileCallback cb) {
        db.collection("profiles").document(uid).get()
                .addOnSuccessListener(cb::onResult)
                .addOnFailureListener(e -> cb.onResult(null));
    }

    // ── 팀 정보 (캐시 → 서버 이중 조회) ──────────────────────────────────────────

    /**
     * teams/{teamId} 를 캐시에서 먼저 조회.
     * 빠른 초기 바인딩용. 실패/없음 시 null 전달.
     */
    public void fetchTeamFromCache(String teamId, TeamDocCallback cb) {
        db.collection("teams").document(teamId)
                .get(Source.CACHE)
                .addOnSuccessListener(cb::onResult)
                .addOnFailureListener(e -> cb.onResult(null));
    }

    /**
     * teams/{teamId} 를 서버에서 최신 조회.
     * 최종 텍스트/이미지 갱신용.
     */
    public void fetchTeamFromServer(String teamId,
                                    TeamDocCallback onSuccess,
                                    @NonNull java.util.function.Consumer<Exception> onError) {
        db.collection("teams").document(teamId)
                .get(Source.SERVER)
                .addOnSuccessListener(onSuccess::onResult)
                .addOnFailureListener(onError::accept);
    }

    // ── 멤버 리스트 ───────────────────────────────────────────────────────────────

    /**
     * teams/{teamId}.members 배열로 UID 목록을 가져온 뒤
     * profiles 를 whereIn 으로 조회해 멤버 프로필 목록을 반환.
     *
     * 포지션 분류(FW/MF/DF/GK)는 UI 책임이므로 Activity 에서 처리.
     */
    @SuppressWarnings("unchecked")
    public void fetchMembers(String teamId, MembersCallback cb) {
        db.collection("teams").document(teamId).get()
                .addOnSuccessListener(teamSnap -> {
                    if (teamSnap == null || !teamSnap.exists()) {
                        cb.onEmpty();
                        return;
                    }

                    List<String> memberUids = (List<String>) teamSnap.get("members");
                    String captainUID     = teamSnap.getString("captainUID");
                    String viceCaptainUID = teamSnap.getString("viceCaptainUID");

                    if (memberUids == null || memberUids.isEmpty()) {
                        cb.onEmpty();
                        return;
                    }

                    db.collection("profiles").whereIn("__name__", memberUids).get()
                            .addOnSuccessListener(profileSnap ->
                                    cb.onResult(profileSnap.getDocuments(), captainUID, viceCaptainUID))
                            .addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    // ── 전적 요약 (실시간) ────────────────────────────────────────────────────────

    /**
     * teamStats/{teamId} 실시간 구독.
     * 반환된 ListenerRegistration 을 Activity 의 onDestroy() 에서 해제할 것.
     */
    public ListenerRegistration listenRecordSummary(String teamId, RecordSummaryCallback cb) {
        return db.collection("teamStats").document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    cb.onStats(
                            safeLong(doc.getLong("games")),
                            safeLong(doc.getLong("wins")),
                            safeLong(doc.getLong("draws")),
                            safeLong(doc.getLong("losses")),
                            safeLong(doc.getLong("goalsFor")),
                            safeLong(doc.getLong("goalsAgainst"))
                    );
                });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private static long safeLong(Long l) { return l != null ? l : 0L; }
}