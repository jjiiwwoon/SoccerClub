package com.example.soccerclub.ui.team;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.soccerclub.R;
import com.example.soccerclub.adapter.TeamMemberAdapter;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.ui.common.RecordsActivity;
import com.example.soccerclub.ui.common.ScheduleActivity;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.google.firebase.storage.FirebaseStorage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyTeamFragment extends Fragment {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private StateLayout state;
    private ImageView teamLogo, teamPhoto, introToggle;
    private TextView teamName, teamIntro, teamRegion, teamSkill, teamAge;
    private TextView teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;
    private Button btnInvite, btnLeaveTeam, btnEditTeam;
    private LinearLayout playerListLayout, recordSection, nextScheduleContainer;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate, tvSeeDetails;
    private TextView tvMemberTitle, btnSeeAllSchedule;
    private View nextScheduleCard, scheduleContent, scheduleLoading;
    private TextView tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView imgHomeLogo, imgAwayLogo;

    // ── 상태 ──────────────────────────────────────────────────────────────────────
    private String teamId = null;
    private String introFullText = "";
    private boolean isIntroExpanded = false;
    private boolean firstImageDrawn = false;

    private ListenerRegistration recordListener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ✅ 팀 정보 수정 후 복귀 시 새로고침
    private final ActivityResultLauncher<Intent> editTeamLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == requireActivity().RESULT_OK
                                && isAdded() && !AppUtils.isEmpty(teamId)) {
                            getTeamInfo(teamId);
                        }
                    });

    // ✅ Fix 2: startActivityForResult 제거 → ActivityResultLauncher 사용
    private final ActivityResultLauncher<Intent> photoLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == requireActivity().RESULT_OK
                                && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                if (teamPhoto != null) teamPhoto.setImageURI(uri);
                                uploadTeamPhotoToFirebase(uri);
                            }
                        }
                    });

    private final Runnable upcomingRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && !AppUtils.isEmpty(teamId)) {
                loadUpcomingSchedule(teamId);
                uiHandler.postDelayed(this, 30_000);
            }
        }
    };

    // ── onCreateView ──────────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_team, container, false);

        state                  = view.findViewById(R.id.stateLayout);
        teamLogo               = view.findViewById(R.id.teamLogo);
        teamPhoto              = view.findViewById(R.id.teamPhoto);
        introToggle            = view.findViewById(R.id.introToggle);
        teamName               = view.findViewById(R.id.teamName);
        teamIntro              = view.findViewById(R.id.teamIntro);
        teamRegion             = view.findViewById(R.id.teamRegion);
        teamSkill              = view.findViewById(R.id.teamSkill);
        teamAge                = view.findViewById(R.id.teamAge);
        teamActivityDay        = view.findViewById(R.id.teamActivityDay);
        teamHomeStadiumName    = view.findViewById(R.id.teamHomeStadiumName);
        teamHomeStadiumAddress = view.findViewById(R.id.teamHomeStadiumAddress);
        btnInvite              = view.findViewById(R.id.btnInvite);
        btnLeaveTeam           = view.findViewById(R.id.btnLeaveTeam);
        btnEditTeam            = view.findViewById(R.id.btnEditTeam);
        playerListLayout       = view.findViewById(R.id.playerListLayout);
        recordSection          = view.findViewById(R.id.recordSection);
        tvGames                = view.findViewById(R.id.tvGames);
        tvWins                 = view.findViewById(R.id.tvWins);
        tvDraws                = view.findViewById(R.id.tvDraws);
        tvLosses               = view.findViewById(R.id.tvLosses);
        tvGF                   = view.findViewById(R.id.tvGF);
        tvGA                   = view.findViewById(R.id.tvGA);
        tvWinRate              = view.findViewById(R.id.tvWinRate);
        tvSeeDetails           = view.findViewById(R.id.tvSeeDetails);
        tvMemberTitle          = view.findViewById(R.id.tvMemberTitle);
        nextScheduleCard       = view.findViewById(R.id.nextScheduleCard);
        nextScheduleContainer  = view.findViewById(R.id.nextScheduleContainer);
        btnSeeAllSchedule      = view.findViewById(R.id.btnSeeAllSchedule);
        scheduleContent        = view.findViewById(R.id.scheduleContent);
        scheduleLoading        = view.findViewById(R.id.scheduleLoading);
        tvNextDateChip         = view.findViewById(R.id.tvNextDateChip);
        tvHomeName             = view.findViewById(R.id.tvHomeName);
        tvAwayName             = view.findViewById(R.id.tvAwayName);
        tvPlace                = view.findViewById(R.id.tvPlace);
        tvAddress              = view.findViewById(R.id.tvAddress);
        imgHomeLogo            = view.findViewById(R.id.imgHomeLogo);
        imgAwayLogo            = view.findViewById(R.id.imgAwayLogo);

        state.showLoading();
        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);

        // 소개 토글
        introToggle.setOnClickListener(v -> {
            isIntroExpanded = !isIntroExpanded;
            if (isIntroExpanded) {
                teamIntro.setMaxLines(Integer.MAX_VALUE);
                teamIntro.setEllipsize(null);
                introToggle.setImageResource(R.drawable.ic_arrow_up);
            } else {
                teamIntro.setMaxLines(10);
                teamIntro.setEllipsize(TextUtils.TruncateAt.END);
                introToggle.setImageResource(R.drawable.ic_arrow_down);
            }
        });

        // ✅ Fix 2: startActivityForResult → photoLauncher
        teamPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            photoLauncher.launch(intent);
        });

        // ✅ Fix 3: getCurrentUser() null 체크 → NPE 방어
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            state.setEmptyMessage("로그인이 필요합니다.");
            state.showEmpty();
            return view;
        }
        String currentUid = user.getUid();

        FirebaseFirestore.getInstance().collection("profiles").document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    if (doc.exists()) {
                        teamId = doc.getString("myTeam");
                        if (!AppUtils.isEmpty(teamId)) {
                            getTeamInfo(teamId);
                        } else {
                            state.setEmptyMessage("소속된 팀이 없습니다.\n팀에 가입하거나 팀을 생성하세요.");
                            state.showEmpty();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    CustomToast.error(requireContext(), "프로필을 불러오지 못했어요.");
                    state.showEmpty();
                });

        if (tvSeeDetails != null) {
            tvSeeDetails.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), RecordsActivity.class);
                intent.putExtra("myTeamId", teamId);
                startActivity(intent);
            });
        }

        if (btnSeeAllSchedule != null) {
            btnSeeAllSchedule.setOnClickListener(v ->
                    startActivity(new Intent(getContext(), ScheduleActivity.class)));
        }
        if (nextScheduleCard != null) {
            nextScheduleCard.setOnClickListener(v ->
                    startActivity(new Intent(getContext(), ScheduleActivity.class)));
        }

        if (btnInvite != null) {
            btnInvite.setOnClickListener(v -> showInviteDialog());
        }

        if (btnLeaveTeam != null) {
            btnLeaveTeam.setOnClickListener(v -> onClickLeaveTeam());
        }

        // ✅ 팀 정보 수정 버튼 (주장/부주장에게만 표시 - bindTeamTextFields에서 처리)
        if (btnEditTeam != null) {
            btnEditTeam.setOnClickListener(v -> {
                if (AppUtils.isEmpty(teamId)) return;
                Intent intent = new Intent(requireContext(), EditTeamActivity.class);
                intent.putExtra("teamId", teamId);
                editTeamLauncher.launch(intent);
            });
        }

        return view;
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    public void onStart() {
        super.onStart();
        if (!AppUtils.isEmpty(teamId)) {
            bindRecordSummary(teamId);
            loadUpcomingSchedule(teamId);
            startUpcomingAutoRefresh();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stopUpcomingAutoRefresh();
    }

    @Override
    public void onDestroyView() {
        if (recordListener != null) {
            recordListener.remove();
            recordListener = null;
        }
        super.onDestroyView();
    }

    // ── 자동 새로고침 ─────────────────────────────────────────────────────────────

    private void startUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
        uiHandler.postDelayed(upcomingRefreshRunnable, 30_000);
    }

    private void stopUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
    }

    // ── 팀 정보 로드 ──────────────────────────────────────────────────────────────

    private void getTeamInfo(String teamId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        firstImageDrawn = false;

        db.collection("teams").document(teamId)
                .get(Source.CACHE)
                .addOnSuccessListener(cacheSnap -> {
                    if (!isAdded() || cacheSnap == null || !cacheSnap.exists()) return;
                    bindTeamTextFields(cacheSnap);
                    startImageLoads(cacheSnap);
                });

        db.collection("teams").document(teamId)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || doc == null || !doc.exists()) {
                        state.setEmptyMessage("팀 정보를 찾을 수 없어요.");
                        state.showEmpty();
                        return;
                    }
                    bindTeamTextFields(doc);
                    startImageLoads(doc);
                    loadPlayerList(teamId);
                    bindRecordSummary(teamId);
                    loadUpcomingSchedule(teamId);

                    uiHandler.postDelayed(() -> {
                        if (!firstImageDrawn && isAdded()) state.showContent();
                    }, 600);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    CustomToast.error(requireContext(), "팀 정보를 불러오지 못했어요.");
                    state.setEmptyMessage("팀 정보를 불러오지 못했어요.");
                    state.showEmpty();
                });
    }

    private void bindTeamTextFields(DocumentSnapshot doc) {
        String teamNameStr    = doc.getString("teamName");
        String intro          = doc.getString("intro");
        String region         = doc.getString("region");
        String skill          = doc.getString("skill");
        String ageRange       = doc.getString("ageRange");
        String activityDayVal = doc.getString("activityDay");
        String homeStadName   = doc.getString("homeStadiumName");
        String stadAddr       = doc.getString("stadium");
        String timeStart      = doc.getString("timeStart");
        String timeEnd        = doc.getString("timeEnd");
        String captainUid     = doc.getString("captainUID");
        String viceCaptainUid = doc.getString("viceCaptainUID");

        // ✅ Fix 3: null 체크 후 uid 접근
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = (user != null) ? user.getUid() : "";

        teamName.setText(AppUtils.safe(teamNameStr));
        introFullText = AppUtils.safe(intro);
        teamIntro.setText(introFullText);
        teamRegion.setText(AppUtils.safe(region));
        teamSkill.setText(AppUtils.safe(skill));
        teamAge.setText(AppUtils.safe(ageRange));

        String activityDisplay = null;
        if (!AppUtils.isEmpty(activityDayVal)) {
            activityDisplay = (!AppUtils.isEmpty(timeStart) && !AppUtils.isEmpty(timeEnd))
                    ? activityDayVal + " | " + timeStart + " ~ " + timeEnd
                    : activityDayVal;
        } else if (!AppUtils.isEmpty(timeStart) && !AppUtils.isEmpty(timeEnd)) {
            activityDisplay = timeStart + " ~ " + timeEnd;
        }

        setTextOrGone(teamActivityDay,        activityDisplay);
        setTextOrGone(teamHomeStadiumName,    homeStadName);
        setTextOrGone(teamHomeStadiumAddress, stadAddr);

        boolean isLeader = currentUid.equals(captainUid) || currentUid.equals(viceCaptainUid);
        if (btnInvite != null)
            btnInvite.setVisibility(isLeader ? View.VISIBLE : View.GONE);
        // ✅ 팀 수정 버튼도 주장/부주장에게만 표시
        if (btnEditTeam != null)
            btnEditTeam.setVisibility(isLeader ? View.VISIBLE : View.GONE);
    }

    private void startImageLoads(DocumentSnapshot doc) {
        if (!isAdded()) return;
        String photoUrl = doc.getString("teamPhotoUrl");
        String logoUrl  = doc.getString("logoUrl");

        RequestOptions opts = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(800, 800);

        if (!AppUtils.isEmpty(photoUrl)) {
            Glide.with(requireContext()).load(photoUrl).apply(opts)
                    .placeholder(R.drawable.default_team_photo)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                                    Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t, boolean b) {
                            if (!firstImageDrawn && isAdded()) { firstImageDrawn = true; state.showContent(); }
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable r,
                                                       Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t,
                                                       com.bumptech.glide.load.DataSource ds, boolean b) {
                            if (!firstImageDrawn && isAdded()) { firstImageDrawn = true; state.showContent(); }
                            return false;
                        }
                    }).into(teamPhoto);
        } else {
            teamPhoto.setImageResource(R.drawable.default_team_photo);
            if (!firstImageDrawn && isAdded()) { firstImageDrawn = true; state.showContent(); }
        }

        if (!AppUtils.isEmpty(logoUrl)) {
            Glide.with(requireContext()).load(logoUrl).apply(opts)
                    .placeholder(R.drawable.ic_shield_gray).into(teamLogo);
        } else {
            teamLogo.setImageResource(R.drawable.ic_shield_gray);
        }
    }

    // ── 멤버 리스트 ───────────────────────────────────────────────────────────────

    private void loadPlayerList(String teamId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("teams").document(teamId).get()
                .addOnSuccessListener(teamDoc -> {
                    if (!isAdded() || !teamDoc.exists()) return;

                    String captainUid     = teamDoc.getString("captainUID");
                    String viceCaptainUid = teamDoc.getString("viceCaptainUID");
                    List<String> members  = (List<String>) teamDoc.get("members");

                    // ✅ Fix 3: null 체크
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    String currentUid = (user != null) ? user.getUid() : "";

                    if (members == null || members.isEmpty()) return;

                    RecyclerView recyclerView = getView() != null
                            ? getView().findViewById(R.id.recyclerViewMembers) : null;
                    if (recyclerView == null) return;

                    db.collection("profiles").whereIn("__name__", members).get()
                            .addOnSuccessListener(profileSnap -> {
                                if (!isAdded()) return;

                                List<DocumentSnapshot> fwDocs = new ArrayList<>(),
                                        mfDocs = new ArrayList<>(),
                                        dfDocs = new ArrayList<>(),
                                        gkDocs = new ArrayList<>();

                                for (DocumentSnapshot p : profileSnap.getDocuments()) {
                                    String pos = p.getString("position");
                                    if (AppUtils.isEmpty(pos)) { mfDocs.add(p); continue; }
                                    switch (pos.trim().toUpperCase()) {
                                        case "FW": fwDocs.add(p); break;
                                        case "MF": mfDocs.add(p); break;
                                        case "DF": dfDocs.add(p); break;
                                        case "GK": gkDocs.add(p); break;
                                        default:   mfDocs.add(p);
                                    }
                                }

                                List<TeamMemberAdapter.MemberItem> items = new ArrayList<>();
                                addPositionGroup(items, "FW (" + fwDocs.size() + ")", fwDocs);
                                addPositionGroup(items, "MF (" + mfDocs.size() + ")", mfDocs);
                                addPositionGroup(items, "DF (" + dfDocs.size() + ")", dfDocs);
                                addPositionGroup(items, "GK (" + gkDocs.size() + ")", gkDocs);

                                if (tvMemberTitle != null)
                                    tvMemberTitle.setText("팀 멤버 (" + profileSnap.size() + ")");

                                final String cap  = captainUid;
                                final String vice = viceCaptainUid;
                                final String uid  = currentUid;

                                TeamMemberAdapter adapter = new TeamMemberAdapter(cap, vice, uid,
                                        (nickname, memberUid) ->
                                                db.collection("teams").document(teamId).get()
                                                        .addOnSuccessListener(s -> {
                                                            String latestVice = s != null
                                                                    ? s.getString("viceCaptainUID") : vice;
                                                            showPlayerOptionsDialog(nickname, memberUid,
                                                                    teamId, latestVice,
                                                                    new TextView(requireContext()));
                                                        }));

                                GridLayoutManager lm = new GridLayoutManager(requireContext(), 2);
                                lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                                    @Override
                                    public int getSpanSize(int pos) {
                                        return adapter.getItemViewType(pos) == TeamMemberAdapter.TYPE_HEADER ? 2 : 1;
                                    }
                                });
                                recyclerView.setLayoutManager(lm);
                                adapter.setItems(items);
                                recyclerView.setAdapter(adapter);
                            })
                            .addOnFailureListener(e -> {
                                if (isAdded())
                                    CustomToast.error(requireContext(), "선수 정보를 불러오지 못했어요.");
                            });
                });
    }

    private void addPositionGroup(List<TeamMemberAdapter.MemberItem> items,
                                  String header, List<DocumentSnapshot> docs) {
        TeamMemberAdapter.MemberItem h = new TeamMemberAdapter.MemberItem();
        h.type   = TeamMemberAdapter.TYPE_HEADER;
        h.header = header;
        items.add(h);
        for (DocumentSnapshot d : docs) {
            TeamMemberAdapter.MemberItem item = new TeamMemberAdapter.MemberItem();
            item.type     = TeamMemberAdapter.TYPE_PLAYER;
            item.uid      = d.getId();
            item.nickname = d.getString("nickname");
            item.photoUrl = d.getString("profileImageUrl");
            items.add(item);
        }
    }

    // ── 전적 요약 ─────────────────────────────────────────────────────────────────

    private void bindRecordSummary(String teamId) {
        if (recordListener != null) recordListener.remove();
        recordListener = FirebaseFirestore.getInstance()
                .collection("teamStats").document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (!isAdded() || doc == null || !doc.exists()) return;
                    long games  = AppUtils.safeLong(doc.getLong("games"), 0L);
                    long wins   = AppUtils.safeLong(doc.getLong("wins"), 0L);
                    long draws  = AppUtils.safeLong(doc.getLong("draws"), 0L);
                    long losses = AppUtils.safeLong(doc.getLong("losses"), 0L);
                    long gf     = AppUtils.safeLong(doc.getLong("goalsFor"), 0L);
                    long ga     = AppUtils.safeLong(doc.getLong("goalsAgainst"), 0L);

                    if (tvGames  != null) tvGames.setText(String.valueOf(games));
                    if (tvWins   != null) tvWins.setText(String.valueOf(wins));
                    if (tvDraws  != null) tvDraws.setText(String.valueOf(draws));
                    if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
                    if (tvGF     != null) tvGF.setText(String.valueOf(gf));
                    if (tvGA     != null) tvGA.setText(String.valueOf(ga));
                    if (tvWinRate != null)
                        tvWinRate.setText(games > 0
                                ? Math.round((wins * 100f) / games) + "%" : "-");
                });
    }

    // ── 다가오는 일정 ─────────────────────────────────────────────────────────────

    private void loadUpcomingSchedule(String teamId) {
        if (!isAdded() || AppUtils.isEmpty(teamId)) return;
        showScheduleLoading(true);
        long now = System.currentTimeMillis();
        FirebaseFirestore.getInstance()
                .collection("schedules")
                .whereEqualTo("teamId", teamId)
                .whereGreaterThan("matchTs", now)
                .orderBy("matchTs")
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!isAdded()) return;
                    showScheduleLoading(false);
                    if (qs == null || qs.isEmpty()) {
                        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
                        showNoUpcomingMessage();
                        return;
                    }
                    bindUpcomingCard(qs.getDocuments().get(0));
                    if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) showScheduleLoading(false);
                });
    }

    private void bindUpcomingCard(DocumentSnapshot doc) {
        if (tvNextDateChip != null) tvNextDateChip.setText(AppUtils.safe(doc.getString("date")));
        if (tvHomeName     != null) tvHomeName.setText(AppUtils.safe(doc.getString("homeTeamName")));
        if (tvAwayName     != null) tvAwayName.setText(AppUtils.safe(doc.getString("awayTeamName")));
        if (tvPlace        != null) tvPlace.setText(AppUtils.safe(doc.getString("stadiumName")));
        if (tvAddress      != null) tvAddress.setText(AppUtils.safe(doc.getString("stadiumAddress")));

        String homeLogo = doc.getString("homeTeamLogoUrl");
        String awayLogo = doc.getString("awayTeamLogoUrl");
        if (imgHomeLogo != null && !AppUtils.isEmpty(homeLogo) && isAdded())
            Glide.with(this).load(homeLogo).circleCrop().into(imgHomeLogo);
        if (imgAwayLogo != null && !AppUtils.isEmpty(awayLogo) && isAdded())
            Glide.with(this).load(awayLogo).circleCrop().into(imgAwayLogo);
    }

    private void showNoUpcomingMessage() {
        if (nextScheduleContainer == null) return;
        nextScheduleContainer.removeAllViews();
        TextView msg = new TextView(requireContext());
        msg.setText("예정된 경기가 없어요.");
        msg.setTextSize(14f);
        msg.setTextColor(0xFF6B7280);
        msg.setGravity(android.view.Gravity.CENTER);
        msg.setPadding(0, dp(24), 0, dp(24));
        nextScheduleContainer.addView(msg);
    }

    private void showScheduleLoading(boolean show) {
        if (scheduleLoading != null) scheduleLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (scheduleContent != null) scheduleContent.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ── 팀 사진 업로드 ────────────────────────────────────────────────────────────

    // ✅ Fix 2+3: ProgressDialog 제거 → 버튼 비활성화 + ExecutorService로 백그라운드 처리
    private void uploadTeamPhotoToFirebase(Uri imageUri) {
        if (AppUtils.isEmpty(teamId) || imageUri == null || !isAdded()) return;

        teamPhoto.setEnabled(false);
        CustomToast.info(requireContext(), "업로드 중...");

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(imageUri);
                if (is == null) throw new IOException("InputStream null");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                byte[] bytes = baos.toByteArray();

                FirebaseStorage.getInstance().getReference()
                        .child("team_photos/" + teamId + ".jpg")
                        .putBytes(bytes)
                        .addOnSuccessListener(t -> t.getStorage().getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    if (!isAdded()) return;
                                    FirebaseFirestore.getInstance()
                                            .collection("teams").document(teamId)
                                            .update("teamPhotoUrl", uri.toString())
                                            .addOnSuccessListener(v -> {
                                                if (!isAdded()) return;
                                                if (teamPhoto != null) teamPhoto.setEnabled(true);
                                                CustomToast.success(requireContext(), "팀 사진이 변경됐어요.");
                                            })
                                            .addOnFailureListener(e -> {
                                                if (!isAdded()) return;
                                                if (teamPhoto != null) teamPhoto.setEnabled(true);
                                                CustomToast.error(requireContext(), "저장 실패: " + e.getMessage());
                                            });
                                }))
                        .addOnFailureListener(e -> {
                            if (!isAdded()) return;
                            if (teamPhoto != null) teamPhoto.setEnabled(true);
                            CustomToast.error(requireContext(), "업로드 실패: " + e.getMessage());
                        });
            } catch (IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (teamPhoto != null) teamPhoto.setEnabled(true);
                        CustomToast.error(requireContext(), "이미지 처리 실패: " + e.getMessage());
                    });
                }
            }
        });
    }

    // ── 팀 초대 다이얼로그 ────────────────────────────────────────────────────────

    private void showInviteDialog() {
        // 레이아웃 없이 코드로 직접 생성
        android.widget.EditText editNickname = new android.widget.EditText(requireContext());
        editNickname.setHint("닉네임 입력");
        int pad = dp(16);
        editNickname.setPadding(pad, pad / 2, pad, pad / 2);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("팀원 초대")
                .setView(editNickname)
                .setPositiveButton("초대 보내기", null) // 아래에서 직접 처리
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v2 -> {
                String nickname = editNickname.getText().toString().trim();
                if (nickname.isEmpty()) {
                    CustomToast.warning(requireContext(), "닉네임을 입력해 주세요.");
                    return;
                }
                FirebaseFirestore.getInstance().collection("profiles")
                        .whereEqualTo("nickname", nickname).get()
                        .addOnSuccessListener(query -> {
                            if (!isAdded()) return;
                            if (!query.isEmpty()) {
                                String receiverUid = query.getDocuments().get(0).getId();
                                sendInviteMessage(receiverUid);
                                dialog.dismiss();
                            } else {
                                CustomToast.warning(requireContext(), "해당 닉네임의 유저가 없어요.");
                            }
                        })
                        .addOnFailureListener(err ->
                                CustomToast.error(requireContext(), "검색에 실패했어요."));
            });
        });

        dialog.show();
    }

    private void sendInviteMessage(String receiverUid) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String senderUid = user.getUid();
        String roomId = senderUid.compareTo(receiverUid) < 0
                ? senderUid + "_" + receiverUid : receiverUid + "_" + senderUid;

        Map<String, Object> message = new HashMap<>();
        message.put("senderId",    senderUid);
        message.put("content",     "[팀 초대] 우리 팀에 합류해보세요!");
        message.put("messageType", "text");
        message.put("timestamp",   System.currentTimeMillis());

        FirebaseFirestore.getInstance().collection("chatRooms").document(roomId)
                .collection("messages").add(message)
                .addOnSuccessListener(v ->
                        CustomToast.success(requireContext(), "초대 메시지를 보냈어요."));
    }

    // ── 팀 탈퇴 ───────────────────────────────────────────────────────────────────

    // ✅ 탈퇴 — 트랜잭션으로 skillAverage 원자적 재계산
    private void onClickLeaveTeam() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String currentUid = user.getUid();

        new AlertDialog.Builder(requireContext())
                .setTitle("팀 탈퇴 확인")
                .setMessage("정말 팀에서 탈퇴하시겠습니까?")
                .setPositiveButton("예", (d, i) -> {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    db.collection("profiles").document(currentUid).get()
                            .addOnSuccessListener(profileSnap -> {
                                String tid = profileSnap.getString("myTeam");
                                if (AppUtils.isEmpty(tid)) {
                                    CustomToast.info(requireContext(), "소속된 팀이 없어요.");
                                    return;
                                }

                                DocumentReference teamRef    = db.collection("teams").document(tid);
                                DocumentReference profileRef = db.collection("profiles").document(currentUid);

                                db.runTransaction(transaction -> {
                                            com.google.firebase.firestore.DocumentSnapshot teamSnap =
                                                    transaction.get(teamRef);
                                            com.google.firebase.firestore.DocumentSnapshot profSnap =
                                                    transaction.get(profileRef);

                                            // 주장은 탈퇴 불가
                                            String captainUid = teamSnap.getString("captainUID");
                                            if (currentUid.equals(captainUid)) {
                                                throw new RuntimeException(
                                                        "팀장은 탈퇴할 수 없어요.\n먼저 '주장 위임'을 해 주세요.");
                                            }

                                            // 실력 값
                                            long skill = profSnap.getLong("skill") != null
                                                    ? profSnap.getLong("skill") : 0L;

                                            // members 배열에서 제거
                                            transaction.update(teamRef, "members",
                                                    FieldValue.arrayRemove(currentUid));
                                            // memberCount, skillSum 감소
                                            transaction.update(teamRef, "memberCount",
                                                    FieldValue.increment(-1L));
                                            transaction.update(teamRef, "skillSum",
                                                    FieldValue.increment(-skill));

                                            // skillAverage 재계산
                                            long curSum   = teamSnap.getLong("skillSum")    != null
                                                    ? teamSnap.getLong("skillSum")    : 0L;
                                            long curCount = teamSnap.getLong("memberCount") != null
                                                    ? teamSnap.getLong("memberCount") : 1L;
                                            long newSum   = Math.max(0, curSum - skill);
                                            long newCount = Math.max(0, curCount - 1);
                                            int  newAvg   = newCount > 0 ? (int)(newSum / newCount) : 0;
                                            transaction.update(teamRef, "skillAverage", newAvg);

                                            // 부주장이면 해제
                                            if (currentUid.equals(
                                                    teamSnap.getString("viceCaptainUID"))) {
                                                transaction.update(teamRef, "viceCaptainUID", "");
                                            }

                                            // 프로필 myTeam 초기화
                                            transaction.update(profileRef, "myTeam", null);
                                            return null;
                                        })
                                        .addOnSuccessListener(v -> {
                                            if (!isAdded()) return;
                                            CustomToast.success(requireContext(), "팀에서 탈퇴했어요.");
                                            teamId = null;
                                            if (nextScheduleCard != null)
                                                nextScheduleCard.setVisibility(View.GONE);
                                            state.setEmptyMessage("소속된 팀이 없습니다.\n팀에 가입하거나 팀을 생성하세요.");
                                            state.showEmpty();
                                        })
                                        .addOnFailureListener(e -> {
                                            if (!isAdded()) return;
                                            String msg = e.getMessage() != null
                                                    ? e.getMessage() : "탈퇴에 실패했어요.";
                                            CustomToast.error(requireContext(), msg);
                                        });
                            })
                            .addOnFailureListener(e ->
                                    CustomToast.error(requireContext(), "프로필 정보를 불러오지 못했어요."));
                })
                .setNegativeButton("아니오", null)
                .show();
    }

    // ── 선수 옵션 다이얼로그 ──────────────────────────────────────────────────────

    private void showPlayerOptionsDialog(String nickname, String uid, String teamId,
                                         String viceCaptainUid, TextView playerViceTag) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_player_options, null);
        TextView txtName           = dialogView.findViewById(R.id.txtPlayerName);
        LinearLayout btnAssignVice    = dialogView.findViewById(R.id.btnAssignVice);
        LinearLayout btnKickPlayer    = dialogView.findViewById(R.id.btnKickPlayer);
        LinearLayout btnAssignCaptain = dialogView.findViewById(R.id.btnAssignCaptain);
        TextView assignViceText    = dialogView.findViewById(R.id.txtAssignViceText);

        txtName.setText(nickname);

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.CustomDialog)
                .setView(dialogView).create();

        // ✅ Fix 3: null 체크
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = (user != null) ? user.getUid() : "";

        // 주장 위임
        if (btnAssignCaptain != null) {
            btnAssignCaptain.setVisibility(View.VISIBLE);
            btnAssignCaptain.setOnClickListener(v -> {
                dialog.dismiss();
                if (currentUid.equals(uid)) {
                    CustomToast.warning(requireContext(), "자기 자신에게는 위임할 수 없어요.");
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("주장 권한 위임")
                        .setMessage(nickname + " 님에게 주장 권한을 위임하시겠습니까?")
                        .setPositiveButton("예", (d, w) ->
                                FirebaseFirestore.getInstance()
                                        .collection("teams").document(teamId)
                                        .update("captainUID", uid)
                                        .addOnSuccessListener(v2 ->
                                                CustomToast.success(requireContext(), "주장 권한이 위임됐어요.")))
                        .setNegativeButton("아니오", null).show();
            });
        }

        // 부주장 지정/해제
        boolean isVice = uid.equals(viceCaptainUid);
        if (assignViceText != null)
            assignViceText.setText(isVice ? "부주장 해제" : "부주장 지정");
        if (btnAssignVice != null) {
            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();
                String newVice = isVice ? "" : uid;
                FirebaseFirestore.getInstance()
                        .collection("teams").document(teamId)
                        .update("viceCaptainUID", newVice)
                        .addOnSuccessListener(v2 ->
                                CustomToast.success(requireContext(),
                                        isVice ? "부주장이 해제됐어요." : "부주장으로 지정됐어요."));
            });
        }

        // ✅ 강퇴 — 트랜잭션으로 skillAverage 원자적 재계산
        if (btnKickPlayer != null) {
            btnKickPlayer.setOnClickListener(v -> {
                dialog.dismiss();
                if (currentUid.equals(uid)) {
                    CustomToast.warning(requireContext(), "자기 자신을 강퇴할 수 없어요.");
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("팀원 강퇴")
                        .setMessage(nickname + " 님을 팀에서 강퇴하시겠습니까?")
                        .setPositiveButton("예", (d, w) -> {
                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                            DocumentReference teamRef    = db.collection("teams").document(teamId);
                            DocumentReference profileRef = db.collection("profiles").document(uid);

                            db.runTransaction(transaction -> {
                                        com.google.firebase.firestore.DocumentSnapshot teamSnap =
                                                transaction.get(teamRef);
                                        com.google.firebase.firestore.DocumentSnapshot profSnap =
                                                transaction.get(profileRef);

                                        long skill = profSnap.getLong("skill") != null
                                                ? profSnap.getLong("skill") : 0L;

                                        transaction.update(teamRef, "members",
                                                FieldValue.arrayRemove(uid));
                                        transaction.update(teamRef, "memberCount",
                                                FieldValue.increment(-1L));
                                        transaction.update(teamRef, "skillSum",
                                                FieldValue.increment(-skill));

                                        long curSum   = teamSnap.getLong("skillSum")    != null
                                                ? teamSnap.getLong("skillSum")    : 0L;
                                        long curCount = teamSnap.getLong("memberCount") != null
                                                ? teamSnap.getLong("memberCount") : 1L;
                                        long newSum   = Math.max(0, curSum - skill);
                                        long newCount = Math.max(0, curCount - 1);
                                        int  newAvg   = newCount > 0 ? (int)(newSum / newCount) : 0;
                                        transaction.update(teamRef, "skillAverage", newAvg);

                                        if (uid.equals(teamSnap.getString("viceCaptainUID"))) {
                                            transaction.update(teamRef, "viceCaptainUID", "");
                                        }

                                        transaction.update(profileRef, "myTeam", null);
                                        return null;
                                    })
                                    .addOnSuccessListener(v2 -> {
                                        if (!isAdded()) return;
                                        CustomToast.success(requireContext(), nickname + "님을 강퇴했어요.");
                                        loadPlayerList(teamId);
                                    })
                                    .addOnFailureListener(e -> {
                                        if (!isAdded()) return;
                                        CustomToast.error(requireContext(),
                                                "강퇴에 실패했어요: " + e.getMessage());
                                    });
                        })
                        .setNegativeButton("아니오", null).show();
            });
        }

        dialog.show();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private void setTextOrGone(TextView tv, String value) {
        if (tv == null) return;
        if (AppUtils.isEmpty(value)) tv.setVisibility(View.GONE);
        else { tv.setVisibility(View.VISIBLE); tv.setText(value); }
    }

    private int dp(int v) {
        return Math.round(v * requireContext().getResources().getDisplayMetrics().density);
    }
}