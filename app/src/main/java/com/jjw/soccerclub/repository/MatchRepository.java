package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.jjw.soccerclub.model.MatchPost;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * 매치글(matches) Firestore 호출 전담.
 *
 * SoccerClub 의 matches 컬렉션에는 두 가지 스키마가 혼재한다.
 * - 신규 : status = "OPEN" 필드 있음
 * - 레거시 : status 필드 없음, timestamp 필드로 정렬
 *
 * 두 쿼리를 모두 여기서 관리한다.
 * ViewModel 은 두 스키마가 있는지 알 필요 없다.
 */
public class MatchRepository {

    public static final int PAGE_SIZE = 20;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 신규 스키마 쿼리 ──────────────────────────────────────────────────────────

    /**
     * status = "OPEN" 인 신규 매치글 한 페이지.
     * @param lastDoc 페이지 커서 (첫 페이지는 null)
     */
    public Task<QuerySnapshot> loadNewPage(DocumentSnapshot lastDoc) {
        Query q = db.collection("matches")
                .whereEqualTo("status", "OPEN")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastDoc != null) q = q.startAfter(lastDoc);
        return q.get();
    }

    // ── 레거시 스키마 쿼리 ────────────────────────────────────────────────────────

    /**
     * status 필드가 없는 레거시 매치글 한 페이지.
     * @param lastDoc 페이지 커서 (첫 페이지는 null)
     */
    public Task<QuerySnapshot> loadLegacyPage(DocumentSnapshot lastDoc) {
        Query q = db.collection("matches")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastDoc != null) q = q.startAfter(lastDoc);
        return q.get();
    }

    // ── 파싱 ─────────────────────────────────────────────────────────────────────

    /**
     * DocumentSnapshot → MatchPost 변환 + 누락 필드 보정.
     *
     * 기존 MatchListFragment 안의 fillFallbacks() 와 toObject() 를 통합.
     */
    public MatchPost parse(DocumentSnapshot d) {
        MatchPost p = d.toObject(MatchPost.class);
        if (p == null) p = new MatchPost();
        p.setMatchId(d.getId());
        fillFallbacks(p, d);
        return p;
    }

    /** 누락/혼재 필드 보정 — 기존 fillFallbacks() 이동 */
    private void fillFallbacks(MatchPost p, DocumentSnapshot d) {
        if (AppUtils.isEmpty(p.getTeamName())) {
            String tn = d.getString("homeTeamName");
            if (!AppUtils.isEmpty(tn)) p.setTeamName(tn);
        }
        if (AppUtils.isEmpty(p.getLogoUrl())) {
            String lu = AppUtils.firstNonEmpty(
                    d.getString("teamLogoUrl"), d.getString("homeTeamLogoUrl"));
            if (!AppUtils.isEmpty(lu)) p.setLogoUrl(lu);
        }
        if (AppUtils.isEmpty(p.getAddress())) {
            String addr = AppUtils.firstNonEmpty(
                    d.getString("stadiumAddress"), d.getString("address"));
            if (!AppUtils.isEmpty(addr)) p.setAddress(addr);
        }
        if (p.getTimestamp() == 0) {
            Long ts = d.getLong("timestamp");
            if (ts != null) p.setTimestamp(ts);
        }
        if (p.getMatchTs() == 0) {
            long calc = DateUtils.computeStartMillis(p.getDate(), p.getTime());
            p.setMatchTs(calc);
        }
    }

    // ── 매치글 등록 ──────────────────────────────────────────────────────────────

    /** 쓰기 결과 콜백 — UI 갱신(토스트, 버튼 상태)은 Activity 책임 */
    public interface WriteCallback {
        void onSuccess();
        void onFailure(@NonNull Exception e);
    }

    /** 매치글 등록 입력값 묶음 — 검증 완료된 값만 전달받는다 */
    public static class NewMatchPost {
        public final String teamId, teamName, teamLogoUrl, region;
        public final int    skill;
        public final String date, startTime, endTime;
        public final String stadiumName, address, description;
        public final String authorUid;

        public NewMatchPost(String teamId, String teamName, String teamLogoUrl,
                            String region, int skill,
                            String date, String startTime, String endTime,
                            String stadiumName, String address, String description,
                            String authorUid) {
            this.teamId = teamId;   this.teamName = teamName;
            this.teamLogoUrl = teamLogoUrl; this.region = region;
            this.skill = skill;
            this.date = date;       this.startTime = startTime; this.endTime = endTime;
            this.stadiumName = stadiumName; this.address = address;
            this.description = description; this.authorUid = authorUid;
        }
    }

    /**
     * 매치글 등록.
     *
     * [변경 전] CreateMatchActivity.submitMatchPost() 가 22개 필드 Map 을
     *   직접 구성해 FirebaseFirestore.getInstance() 로 add().
     *
     * [변경 후] 문서 스키마 구성과 쓰기를 이 Repository 가 담당.
     *   저장되는 필드명/값/파생값(matchTs, endTs, weekday) 계산은 변경 전과 동일.
     */
    public void createPost(@NonNull NewMatchPost p, @NonNull WriteCallback callback) {
        long matchTs  = DateUtils.computeStartMillis(p.date, p.startTime);
        long endTs    = DateUtils.computeEndMillis(p.date,
                p.startTime + " ~ " + p.endTime);
        long nowMs    = System.currentTimeMillis();
        String weekday = DateUtils.getKoreanWeekday(p.date);

        Map<String, Object> data = new HashMap<>();
        data.put("teamId",         p.teamId);
        data.put("teamName",       p.teamName);
        data.put("logoUrl",        p.teamLogoUrl);
        data.put("teamLogoUrl",    p.teamLogoUrl);
        data.put("date",           p.date);
        data.put("time",           p.startTime + " ~ " + p.endTime);
        data.put("timeStart",      p.startTime);
        data.put("timeEnd",        p.endTime);
        data.put("matchTs",        matchTs);
        data.put("endTs",          endTs);
        data.put("timestamp",      nowMs);
        data.put("weekday",        weekday);
        data.put("stadiumName",    p.stadiumName);
        data.put("stadiumAddress", p.address);
        data.put("address",        p.address);
        data.put("skill",          p.skill);
        data.put("description",    p.description);
        data.put("status",         "OPEN");
        data.put("region",         p.region);
        data.put("authorUid",      p.authorUid);
        data.put("createdBy",      p.authorUid);
        data.put("createdAt",      Timestamp.now());

        db.collection("matches").add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }
}