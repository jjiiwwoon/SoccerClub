package com.jjw.soccerclub.repository;

import com.jjw.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 전적(Records) 화면 Firestore 호출 전담.
 *
 * [변경 전] RecordsActivity 가 직접 하던 일
 *   - teamStats 실시간 리스너 (loadTeamStats)
 *   - profiles.whereEqualTo("myTeam") 로 팀원 조회 (loadTeamMembers)
 *   - matches 쿼리 + goalEvents 집계 (loadPersonalStats)
 *
 * [변경 후] 이 Repository 가 모두 담당.
 *   ViewModel 은 Firestore 코드 없이 메서드 호출 + 콜백만 처리.
 *
 * ※ loadTeamMembers 방식 변경:
 *   기존 → profiles.whereEqualTo("myTeam", teamId)
 *   변경 → teams/{teamId}.members 배열로 UID 조회 후 profiles 일괄 fetch
 *   이유 → profiles 쿼리는 myTeam 필드 미갱신 시 누락 가능, 팀 문서 기준이 더 정확
 */
public class RecordsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    public interface TeamStatsCallback {
        void onStats(int games, int wins, int draws, int losses, int gf, int ga);
    }

    public interface MembersCallback {
        void onMembers(Map<String, MemberInfo> members);
        void onError(String msg);
    }

    public interface PersonalStatsCallback {
        void onStats(List<PlayerStat> stats);
        void onError(String msg);
    }

    // ── 데이터 모델 ───────────────────────────────────────────────────────────────

    public static class MemberInfo {
        public String uid = "", nickname = "", photoUrl = "";
    }

    public static class PlayerStat {
        public String uid = "", nickname = "", photoUrl = "";
        public int games = 0, goals = 0, assists = 0;
    }

    // ── teamStats 실시간 리스너 ───────────────────────────────────────────────────

    /**
     * teamStats 문서를 실시간으로 구독.
     * 반환된 ListenerRegistration 을 ViewModel 의 onCleared() 에서 해제할 것.
     */
    public ListenerRegistration listenTeamStats(String teamId, TeamStatsCallback cb) {
        return db.collection("teamStats").document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    cb.onStats(
                            safeInt(doc.getLong("games")),
                            safeInt(doc.getLong("wins")),
                            safeInt(doc.getLong("draws")),
                            safeInt(doc.getLong("losses")),
                            safeInt(doc.getLong("goalsFor")),
                            safeInt(doc.getLong("goalsAgainst"))
                    );
                });
    }

    // ── 팀원 목록 조회 ────────────────────────────────────────────────────────────

    /**
     * teams/{teamId}.members 배열로 UID 목록을 가져온 뒤
     * profiles 를 일괄 조회해 MemberInfo 맵을 반환.
     */
    @SuppressWarnings("unchecked")
    public void fetchMembers(String teamId, MembersCallback cb) {
        db.collection("teams").document(teamId).get()
                .addOnSuccessListener(teamDoc -> {
                    if (!teamDoc.exists()) {
                        cb.onError("팀 정보를 찾을 수 없어요.");
                        return;
                    }
                    List<String> uids = (List<String>) teamDoc.get("members");
                    if (uids == null || uids.isEmpty()) {
                        cb.onMembers(new HashMap<>());
                        return;
                    }

                    List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
                    for (String uid : uids) {
                        tasks.add(db.collection("profiles").document(uid).get());
                    }

                    Tasks.whenAllSuccess(tasks)
                            .addOnSuccessListener(results -> {
                                Map<String, MemberInfo> map = new HashMap<>();
                                for (Object obj : results) {
                                    DocumentSnapshot snap = (DocumentSnapshot) obj;
                                    if (!snap.exists()) continue;
                                    MemberInfo m = new MemberInfo();
                                    m.uid      = snap.getId();
                                    m.nickname = AppUtils.safe(snap.getString("nickname"));
                                    m.photoUrl = AppUtils.safe(snap.getString("profileImageUrl"));
                                    if (!AppUtils.isEmpty(m.nickname)) map.put(m.uid, m);
                                }
                                cb.onMembers(map);
                            })
                            .addOnFailureListener(e -> cb.onError("팀원 조회 실패: " + e.getMessage()));
                })
                .addOnFailureListener(e -> cb.onError("팀 조회 실패: " + e.getMessage()));
    }

    // ── 개인 기록 집계 ────────────────────────────────────────────────────────────

    /**
     * matches 컬렉션에서 해당 팀의 완료된 경기를 조회해
     * 팀원별 games / goals / assists 를 집계.
     *
     * [수정] 경기 참여 카운트를 팀원 전원이 아닌,
     *        votes 서브컬렉션에서 실제 참석(attend)한 멤버만 카운트.
     */
    @SuppressWarnings("unchecked")
    public void fetchPersonalStats(String teamId,
                                   Map<String, MemberInfo> memberMap,
                                   PersonalStatsCallback cb) {
        db.collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    // 모든 팀원을 0 기록으로 초기화
                    Map<String, PlayerStat> statMap = new HashMap<>();
                    for (MemberInfo m : memberMap.values()) {
                        PlayerStat ps = new PlayerStat();
                        ps.uid      = m.uid;
                        ps.nickname = m.nickname;
                        ps.photoUrl = m.photoUrl;
                        statMap.put(m.uid, ps);
                    }

                    List<DocumentSnapshot> matchDocs = snap.getDocuments();
                    if (matchDocs.isEmpty()) {
                        cb.onStats(new ArrayList<>(statMap.values()));
                        return;
                    }

                    // ── 1) goalEvents 기반 득점/도움 집계 (기존과 동일) ────────
                    List<String> eventIds = new ArrayList<>();

                    for (DocumentSnapshot d : matchDocs) {
                        // createdFromEventId 수집 (투표 조회용)
                        String eventId = d.getString("createdFromEventId");
                        if (eventId != null) {
                            eventIds.add(eventId);
                        }

                        // 골/어시스트 집계
                        List<Map<String, Object>> events =
                                (List<Map<String, Object>>) d.get("goalEvents");
                        if (events == null) continue;

                        for (Map<String, Object> ev : events) {
                            String scorerId = safeStr(ev, "scorerId");
                            String assistId = safeStr(ev, "assistId");

                            PlayerStat scorer = statMap.get(scorerId);
                            if (scorer != null) scorer.goals++;

                            PlayerStat assister = statMap.get(assistId);
                            if (assister != null) assister.assists++;
                        }
                    }

                    // ── 2) votes 조회 → 실제 참석자만 games 카운트 ────────────
                    if (eventIds.isEmpty()) {
                        cb.onStats(new ArrayList<>(statMap.values()));
                        return;
                    }

                    List<Task<QuerySnapshot>> voteTasks = new ArrayList<>();
                    for (String eventId : eventIds) {
                        voteTasks.add(
                                db.collection("schedules").document(teamId)
                                        .collection("events").document(eventId)
                                        .collection("votes")
                                        .whereEqualTo("status", "attend")
                                        .get()
                        );
                    }

                    Tasks.whenAllSuccess(voteTasks)
                            .addOnSuccessListener(voteResults -> {
                                for (Object obj : voteResults) {
                                    QuerySnapshot voteSnap = (QuerySnapshot) obj;
                                    for (DocumentSnapshot voteDoc : voteSnap.getDocuments()) {
                                        String uid = voteDoc.getString("uid");
                                        if (uid == null) uid = voteDoc.getId();
                                        PlayerStat ps = statMap.get(uid);
                                        if (ps != null) ps.games++;
                                    }
                                }
                                cb.onStats(new ArrayList<>(statMap.values()));
                            })
                            .addOnFailureListener(e -> {
                                // 투표 조회 실패 시에도 득점/도움은 보여줌
                                cb.onStats(new ArrayList<>(statMap.values()));
                            });
                })
                .addOnFailureListener(e -> cb.onError("개인기록 로드 실패: " + e.getMessage()));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private static int safeInt(Long l) { return l != null ? l.intValue() : 0; }

    private static String safeStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }
}