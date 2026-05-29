package com.jjw.soccerclub.repository;

import com.jjw.soccerclub.adapter.RecruitAdapter;
import com.jjw.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 모집글(recruitPosts) Firestore 호출 전담.
 *
 * ViewModel 이나 Fragment 는 이 클래스만 통해 데이터를 요청한다.
 * Firestore 가 어떤 컬렉션을 쓰는지, 어떻게 파싱하는지는 여기서만 안다.
 */
public class RecruitRepository {

    public static final int PAGE_SIZE = 20;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 데이터 요청 ───────────────────────────────────────────────────────────────

    /**
     * 모집글 한 페이지 로드.
     *
     * @param lastDoc 페이지 커서 — 첫 페이지는 null 전달
     * @return Task<QuerySnapshot> : 성공/실패 처리는 ViewModel 에서 담당
     */
    public Task<QuerySnapshot> loadPage(DocumentSnapshot lastDoc) {
        Query q = db.collection("recruitPosts")
                .whereEqualTo("status", "open")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastDoc != null) q = q.startAfter(lastDoc);

        return q.get();
    }

    // ── 파싱 ─────────────────────────────────────────────────────────────────────

    /**
     * Firestore DocumentSnapshot → RecruitItem 변환.
     *
     * 기존 RecruitListFragment 안에 흩어져 있던 파싱 로직을 한 곳으로 통합.
     * 필드명 변경이 생겨도 이 메서드만 수정하면 된다.
     */
    @SuppressWarnings("unchecked")
    public RecruitAdapter.RecruitItem parse(DocumentSnapshot d) {
        RecruitAdapter.RecruitItem it = new RecruitAdapter.RecruitItem();

        it.id             = d.getId();
        it.teamName       = d.getString("teamName");
        it.teamLogoUrl    = AppUtils.firstNonEmpty(
                d.getString("teamLogoUrl"), d.getString("logoUrl"));
        it.date           = d.getString("date");
        it.time           = d.getString("time");
        it.weekday        = d.getString("weekday");
        it.stadiumName    = AppUtils.firstNonEmpty(
                d.getString("stadiumName"), d.getString("stadium"));
        it.stadiumAddress = AppUtils.firstNonEmpty(
                d.getString("address"), d.getString("stadiumAddress"));
        it.recruitType    = d.getString("recruitType");
        it.relativeTime   = d.getString("relativeTime");

        Long skillMinL = d.getLong("skillMin");
        Long skillMaxL = d.getLong("skillMax");
        it.skillMin = skillMinL != null ? skillMinL.intValue() : null;
        it.skillMax = skillMaxL != null ? skillMaxL.intValue() : null;

        List<String> pos = (List<String>) d.get("positions");
        it.positions = pos != null ? pos : new ArrayList<>();

        // 타임스탬프 — 여러 필드명 혼재 대응 (AppUtils.normalizeToMillis 활용)
        Long createdAtMs  = d.getLong("createdAtMs");
        Timestamp createdAtTs = d.getTimestamp("createdAt");
        Long postTs  = d.getLong("postTs");
        Long matchTs = d.getLong("matchTs");

        it.createdAtMs = createdAtMs  != null ? createdAtMs  : 0L;
        it.createdAt   = createdAtTs  != null ? createdAtTs.toDate().getTime() : 0L;
        it.postTs      = postTs       != null ? postTs       : 0L;
        it.matchTs     = matchTs      != null ? matchTs      : 0L;

        return it;
    }
}