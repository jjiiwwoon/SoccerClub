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

/**
 * 신청 목록 화면.
 *
 * [변경 전] Activity 가 직접 하던 일 (~400줄)
 *   - Firestore db 직접 보유
 *   - collectMine() / collectApplied() Firestore 쿼리 직접 작성
 *   - Tasks.whenAllSuccess() 비동기 병렬 처리 직접 관리
 *   - profileLoaded 플래그 직접 관리
 *   - handleAccept/handleReject 트랜잭션 직접 처리
 *   - sessionMaxTs 뱃지 계산 직접 처리
 *   - onResume() 마다 loadData() 재호출
 *
 * [변경 후] Activity 가 하는 일 (~100줄)
 *   - viewModel.items.observe → adapter.setItems()
 *   - viewModel.isLoading.observe → 로딩 처리 (StateLayout 없으므로 생략)
 *   - viewModel.actionResult.observe → Toast 표시
 *   - 버튼 클릭 → viewModel.load() / accept() / reject() 위임
 *   - Firestore 코드 없음
 *
 * ✅ 주의: activity_applicaitons_list.xml 에 StateLayout 이 없으므로
 *   state 관련 코드는 포함하지 않는다.
 */
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

        // 목록
        viewModel.items.observe(this, items ->
                adapter.setItems(
                        items != null ? items : Collections.emptyList(),
                        mineSelected
                                ? ApplicationsAdapter.TYPE_MINE
                                : ApplicationsAdapter.TYPE_APPLIED));

        // 수락/거절 결과 토스트
        viewModel.actionResult.observe(this, msg -> {
            if (msg != null) CustomToast.success(this, msg);
            else             CustomToast.error(this, "처리에 실패했어요. 다시 시도해 주세요.");
        });

        // ✅ 변경 전: onCreate 에서 db.collection("profiles") 직접 호출
        // ✅ 변경 후: ViewModel.init() 에 위임 — 화면 회전 시 재조회 없음
        viewModel.init(user.getUid(), "mine", "all");
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