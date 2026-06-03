package com.jjw.soccerclub.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.ApplicationsAdapter;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.ui.chat.ChatRoomActivity;
import com.jjw.soccerclub.viewmodel.ApplicationsViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ApplicationsListActivity extends AppCompatActivity {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private TextView         btnSubjectMine, btnSubjectApplied;
    private TextView         chipTypeAll, chipTypeRecruit, chipTypeMatch;
    private RecyclerView     recycler;
    private ApplicationsAdapter adapter;

    // ── ViewModel ────────────────────────────────────────────────────────────────
    private ApplicationsViewModel viewModel;

    // ── 현재 탭/필터 상태 ─────────────────────────────────────────────────────────
    private boolean mineSelected = true;
    private String  typeFilter   = "all";

    // ★ 세션 중 본 글별 최대 신청자 timestamp (prefKey → maxTs)
    private final Map<String, Long> sessionMaxApplicantTs = new HashMap<>();

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applicaitons_list);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        // 뷰 바인딩
        btnSubjectMine    = findViewById(R.id.btnSubjectMine);
        btnSubjectApplied = findViewById(R.id.btnSubjectApplied);
        chipTypeAll       = findViewById(R.id.chipTypeAll);
        chipTypeRecruit   = findViewById(R.id.chipTypeRecruit);
        chipTypeMatch     = findViewById(R.id.chipTypeMatch);
        recycler          = findViewById(R.id.recycler);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ApplicationsAdapter();
        adapter.setOnItemClickListener(new ApplicationsAdapter.OnItemClickListener() {
            @Override
            public void onPostClicked(ApplicationsAdapter.Item item) {}

            @Override
            public void onApplicantAccept(ApplicationsAdapter.Item post,
                                          ApplicationsAdapter.Applicant applicant) {
                viewModel.accept(post, applicant);
                adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");
            }

            @Override
            public void onApplicantReject(ApplicationsAdapter.Item post,
                                          ApplicationsAdapter.Applicant applicant) {
                viewModel.reject(post, applicant);
                adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "rejected");
            }

            @Override
            public void onApplicantChat(ApplicationsAdapter.Item post,
                                        ApplicationsAdapter.Applicant applicant) {
                openChat(applicant.applicantUserId);
            }
        });
        recycler.setAdapter(adapter);

        // 버튼 초기 스타일
        setBtnStyle(true);
        setTypeSelected("all");

        // 탭 버튼
        btnSubjectMine.setOnClickListener(v -> {
            mineSelected = true;
            setBtnStyle(true);
            viewModel.load("mine", typeFilter);
        });
        btnSubjectApplied.setOnClickListener(v -> {
            mineSelected = false;
            setBtnStyle(false);
            viewModel.load("applied", typeFilter);
        });

        // 필터 칩
        chipTypeAll.setOnClickListener(v -> {
            typeFilter = "all"; setTypeSelected("all");
            viewModel.load(mineSelected ? "mine" : "applied", "all");
        });
        chipTypeRecruit.setOnClickListener(v -> {
            typeFilter = "recruit"; setTypeSelected("recruit");
            viewModel.load(mineSelected ? "mine" : "applied", "recruit");
        });
        chipTypeMatch.setOnClickListener(v -> {
            typeFilter = "match"; setTypeSelected("match");
            viewModel.load(mineSelected ? "mine" : "applied", "match");
        });

        // ── ViewModel 초기화 + observe ────────────────────────────────────────────

        viewModel = new ViewModelProvider(this).get(ApplicationsViewModel.class);

        // ★ 목록 — SharedPreferences 기반 NEW 계산 후 adapter에 세팅
        viewModel.items.observe(this, items -> {
            List<ApplicationsAdapter.Item> list =
                    items != null ? items : Collections.emptyList();
            computeSessionBadgesAndApply(list);
            adapter.setItems(list,
                    mineSelected
                            ? ApplicationsAdapter.TYPE_MINE
                            : ApplicationsAdapter.TYPE_APPLIED);
        });

        // 수락/거절 결과 토스트
        viewModel.actionResult.observe(this, msg -> {
            if (msg != null) CustomToast.success(this, msg);
            else             CustomToast.error(this, "처리에 실패했어요. 다시 시도해 주세요.");
        });

        viewModel.init(user.getUid(), "mine", "all");
    }

    // ★ 화면을 떠날 때 "본 시간" 저장
    @Override
    protected void onPause() {
        super.onPause();
        persistLastSeenForThisSession();
    }

    // ── NEW 뱃지 계산 ─────────────────────────────────────────────────────────────

    /**
     * SharedPreferences 기반 NEW 계산.
     * - 각 글의 신청자 timestamp와 lastSeen 비교
     * - 새 신청자에 NEW 키 세팅
     * - 세션 중 최대 ts 누적 → onPause에서 저장
     */
    private void computeSessionBadgesAndApply(List<ApplicationsAdapter.Item> list) {
        Set<String> newKeys = new HashSet<>();

        for (ApplicationsAdapter.Item it : list) {
            if (it.applicants == null) continue;

            String postType = (it.postType == null) ? "" : it.postType.toLowerCase(Locale.ROOT);
            String postId   = (it.postId == null) ? "" : it.postId;

            long lastSeenTs = getLastSeenTs(postType, postId);
            long maxTs = lastSeenTs;
            boolean hasNew = false;

            for (ApplicationsAdapter.Applicant a : it.applicants) {
                long ts = a.timestamp > 0 ? a.timestamp : 0;
                if (ts > lastSeenTs) {
                    newKeys.add(buildApplicantKey(postType, postId, a.applicantDocId));
                    hasNew = true;
                }
                if (ts > maxTs) maxTs = ts;
            }

            it.hasSessionNew = hasNew;

            // 세션 중 본 최대값 누적 → onPause에서 일괄 저장
            if (maxTs > 0) {
                String prefKey = buildPrefKey(postType, postId);
                sessionMaxApplicantTs.put(prefKey,
                        Math.max(sessionMaxApplicantTs.getOrDefault(prefKey, 0L), maxTs));
            }
        }

        if (adapter != null) adapter.setSessionNewApplicantKeys(newKeys);
        persistLastSeenForThisSession();
    }

    /**
     * 세션 중 확인한 '각 게시글의 최대 신청 ts'를 SharedPreferences에 저장.
     * RecruitMatchFragment와 같은 "badge_prefs" + 같은 키 포맷 사용.
     */
    private void persistLastSeenForThisSession() {
        if (sessionMaxApplicantTs.isEmpty()) return;

        android.content.SharedPreferences.Editor ed =
                getSharedPreferences("badge_prefs", MODE_PRIVATE).edit();

        for (Map.Entry<String, Long> e : sessionMaxApplicantTs.entrySet()) {
            long tsMs = normalizeToMillis(e.getValue());
            if (tsMs > 0) ed.putLong(e.getKey(), tsMs);
        }
        ed.apply();
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────────────

    private long getLastSeenTs(String postType, String postId) {
        String key = buildPrefKey(postType, postId);
        return getSharedPreferences("badge_prefs", MODE_PRIVATE).getLong(key, 0L);
    }

    /** RecruitMatchFragment와 동일한 키 포맷 */
    private static String buildPrefKey(String postType, String postId) {
        return "last_seen_" + (postType == null ? "" : postType)
                + "_" + (postId == null ? "" : postId);
    }

    private static String buildApplicantKey(String postType, String postId, String applicantDocId) {
        return (postType == null ? "" : postType.toLowerCase(Locale.ROOT))
                + ":" + (postId == null ? "" : postId)
                + ":" + (applicantDocId == null ? "" : applicantDocId);
    }

    private static long normalizeToMillis(Long v) {
        if (v == null || v <= 0) return 0L;
        return (v > 2_000_000_000L) ? v : v * 1000L;
    }

    // ── 채팅방 열기 ───────────────────────────────────────────────────────────────

    private void openChat(String otherUid) {
        if (otherUid == null || otherUid.isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String myUid  = user.getUid();
        String roomId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid : otherUid + "_" + myUid;

        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra("roomId", roomId);
        startActivity(intent);
    }

    // ── UI 헬퍼 ──────────────────────────────────────────────────────────────────

    private void setBtnStyle(boolean mineActive) {
        if (btnSubjectMine == null || btnSubjectApplied == null) return;
        btnSubjectMine.setTextColor(mineActive ? 0xFF1976D2 : 0xFF888888);
        btnSubjectApplied.setTextColor(mineActive ? 0xFF888888 : 0xFF1976D2);
    }

    private void setTypeSelected(String type) {
        if (chipTypeAll == null || chipTypeRecruit == null || chipTypeMatch == null) return;
        chipTypeAll.setTextColor("all".equals(type)         ? 0xFF1976D2 : 0xFF888888);
        chipTypeRecruit.setTextColor("recruit".equals(type) ? 0xFF1976D2 : 0xFF888888);
        chipTypeMatch.setTextColor("match".equals(type)     ? 0xFF1976D2 : 0xFF888888);
    }
}