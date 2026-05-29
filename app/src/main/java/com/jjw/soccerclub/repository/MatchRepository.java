package com.jjw.soccerclub.repository;

import com.jjw.soccerclub.model.MatchPost;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

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
}