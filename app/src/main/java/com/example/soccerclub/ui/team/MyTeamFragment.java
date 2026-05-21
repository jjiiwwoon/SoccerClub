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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.soccerclub.R;
import com.example.soccerclub.adapter.TeamMemberAdapter;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.model.Team;
import com.example.soccerclub.ui.common.RecordsActivity;
import com.example.soccerclub.ui.common.ScheduleActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.viewmodel.MyTeamViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyTeamFragment extends Fragment {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private StateLayout  state;
    private ImageView    teamLogo, teamPhoto, introToggle;
    private TextView     teamName, teamIntro, teamRegion, teamSkill, teamAge;
    private TextView     teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;
    private Button       btnInvite, btnLeaveTeam, btnEditTeam, btnJoinRequests;
    private LinearLayout playerListLayout, recordSection, nextScheduleContainer;
    private TextView     tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate, tvSeeDetails;
    private TextView     tvMemberTitle, btnSeeAllSchedule;
    private View         nextScheduleCard, scheduleContent, scheduleLoading;
    private TextView     tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView    imgHomeLogo, imgAwayLogo;

    // ── 상태 ──────────────────────────────────────────────────────────────────────
    private String  teamId         = null;
    private String  captainUid     = "";
    private String  viceCaptainUid = "";
    private String  introFullText  = "";
    private boolean isIntroExpanded = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ── ViewModel ────────────────────────────────────────────────────────────────
    private MyTeamViewModel viewModel;

    // ── ActivityResultLauncher ────────────────────────────────────────────────────

    private final ActivityResultLauncher<Intent> editTeamLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == requireActivity().RESULT_OK && isAdded()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null && viewModel != null) {
                                if (state != null) state.showLoading();
                                viewModel.reload(user.getUid());
                            }
                        }
                    });

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
            if (isAdded() && viewModel != null && !AppUtils.isEmpty(teamId)) {
                // ✅ 수정: refreshNextSchedule(teamId) 호출
                viewModel.refreshNextSchedule(teamId);
                uiHandler.postDelayed(this, 30_000);
            }
        }
    };

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

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
        btnJoinRequests        = view.findViewById(R.id.btnJoinRequests);
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

        if (state != null) state.showLoading();
        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);

        if (introToggle != null) {
            introToggle.setOnClickListener(v -> {
                isIntroExpanded = !isIntroExpanded;
                if (teamIntro != null) {
                    teamIntro.setMaxLines(isIntroExpanded ? Integer.MAX_VALUE : 10);
                    teamIntro.setEllipsize(isIntroExpanded ? null : TextUtils.TruncateAt.END);
                }
                introToggle.setImageResource(isIntroExpanded
                        ? R.drawable.ic_arrow_up : R.drawable.ic_arrow_down);
            });
        }

        if (teamPhoto != null) {
            teamPhoto.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                photoLauncher.launch(intent);
            });
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (state != null) { state.setEmptyMessage("로그인이 필요합니다."); state.showEmpty(); }
            return;
        }

        viewModel = new ViewModelProvider(this).get(MyTeamViewModel.class);

        viewModel.hasNoTeam.observe(getViewLifecycleOwner(), noTeam -> {
            if (noTeam != null && noTeam && state != null) {
                state.setEmptyMessage("소속된 팀이 없습니다.\n팀에 가입하거나 팀을 생성하세요.");
                state.showEmpty();
            }
        });

        viewModel.teamInfo.observe(getViewLifecycleOwner(), team -> {
            if (team == null) return;
            teamId = team.getTeamId();
            bindTeamInfo(team);
            setupButtonsForTeam(team, user.getUid());
            startUpcomingAutoRefresh();
        });

        viewModel.teamStats.observe(getViewLifecycleOwner(), doc -> {
            if (doc != null && doc.exists()) bindRecordSummary(doc);
        });

        viewModel.nextSchedule.observe(getViewLifecycleOwner(), doc -> {
            showScheduleLoading(false);
            bindNextSchedule(doc);
        });

        viewModel.memberProfiles.observe(getViewLifecycleOwner(), profiles -> {
            if (profiles != null) bindMemberList(profiles);
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading && state != null) state.showLoading();
        });

        viewModel.loadIfNeeded(user.getUid());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!AppUtils.isEmpty(teamId)) startUpcomingAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopUpcomingAutoRefresh();
    }

    @Override
    public void onDestroyView() {
        stopUpcomingAutoRefresh();
        super.onDestroyView();
    }

    // ── UI 바인딩 — 팀 기본 정보 ─────────────────────────────────────────────────

    private void bindTeamInfo(Team team) {
        captainUid     = AppUtils.safe(team.getCaptainUID());
        viceCaptainUid = AppUtils.safe(team.getViceCaptainUID());

        if (teamName != null) teamName.setText(AppUtils.safe(team.getTeamName()));

        introFullText = AppUtils.safe(team.getIntro());
        if (teamIntro != null) {
            teamIntro.setText(introFullText);
            teamIntro.setMaxLines(10);
            teamIntro.setEllipsize(TextUtils.TruncateAt.END);
            isIntroExpanded = false;
        }

        if (teamRegion != null) teamRegion.setText(AppUtils.safe(team.getRegion()));
        if (teamAge    != null) teamAge.setText(AppUtils.safe(team.getAgeRange()));

        Integer avg = team.getSkillAverage();
        if (teamSkill != null)
            teamSkill.setText(avg != null && avg > 0 ? String.valueOf(avg) : "-");

        String day    = AppUtils.safe(team.getActivityDay());
        String tStart = AppUtils.safe(team.getTimeStart());
        String tEnd   = AppUtils.safe(team.getTimeEnd());
        String actDisplay = null;
        if (!AppUtils.isEmpty(day)) {
            actDisplay = (!AppUtils.isEmpty(tStart) && !AppUtils.isEmpty(tEnd))
                    ? day + " | " + tStart + " ~ " + tEnd : day;
        } else if (!AppUtils.isEmpty(tStart) && !AppUtils.isEmpty(tEnd)) {
            actDisplay = tStart + " ~ " + tEnd;
        }
        setTextOrGone(teamActivityDay, actDisplay);
        setTextOrGone(teamHomeStadiumName, team.getStadium());

        loadTeamImages(team);
        if (state != null) state.showContent();
    }

    private void loadTeamImages(Team team) {
        if (!isAdded()) return;
        String logoUrl = AppUtils.safe(team.getLogoUrl());
        RequestOptions opts = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL).override(400, 400);

        if (teamPhoto != null) teamPhoto.setImageResource(R.drawable.default_team_photo);

        if (teamLogo != null) {
            if (!AppUtils.isEmpty(logoUrl)) {
                Glide.with(this).load(logoUrl).apply(opts)
                        .placeholder(R.drawable.ic_shield_gray).into(teamLogo);
            } else {
                teamLogo.setImageResource(R.drawable.ic_shield_gray);
            }
        }
    }

    // ── UI 바인딩 — 버튼 권한 설정 ───────────────────────────────────────────────

    private void setupButtonsForTeam(Team team, String currentUid) {
        boolean isCaptain    = currentUid.equals(captainUid);
        boolean isVice       = currentUid.equals(viceCaptainUid);
        boolean isPrivileged = isCaptain || isVice;

        if (btnInvite != null) {
            btnInvite.setVisibility(isPrivileged ? View.VISIBLE : View.GONE);
            btnInvite.setOnClickListener(v -> showInviteDialog());
        }
        if (btnLeaveTeam != null) {
            btnLeaveTeam.setVisibility(isCaptain ? View.GONE : View.VISIBLE);
            btnLeaveTeam.setOnClickListener(v -> showLeaveTeamDialog(currentUid));
        }
        if (btnEditTeam != null) {
            btnEditTeam.setVisibility(isCaptain ? View.VISIBLE : View.GONE);
            btnEditTeam.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), EditTeamActivity.class);
                intent.putExtra("teamId", teamId);
                editTeamLauncher.launch(intent);
            });
        }
        if (btnJoinRequests != null) {
            btnJoinRequests.setVisibility(isPrivileged ? View.VISIBLE : View.GONE);
            btnJoinRequests.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), JoinRequestsActivity.class);
                intent.putExtra("teamId", teamId);
                startActivity(intent);
            });
        }
        if (tvSeeDetails != null) {
            tvSeeDetails.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), RecordsActivity.class);
                intent.putExtra("teamId", teamId);
                startActivity(intent);
            });
        }
        if (btnSeeAllSchedule != null) {
            btnSeeAllSchedule.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ScheduleActivity.class);
                intent.putExtra("teamId", teamId);
                startActivity(intent);
            });
        }
    }

    // ── UI 바인딩 — 전적 통계 ────────────────────────────────────────────────────

    private void bindRecordSummary(DocumentSnapshot doc) {
        if (!isAdded()) return;
        long games  = AppUtils.safeLong(doc.getLong("games"),       0L);
        long wins   = AppUtils.safeLong(doc.getLong("wins"),        0L);
        long draws  = AppUtils.safeLong(doc.getLong("draws"),       0L);
        long losses = AppUtils.safeLong(doc.getLong("losses"),      0L);
        long gf     = AppUtils.safeLong(doc.getLong("goalsFor"),    0L);
        long ga     = AppUtils.safeLong(doc.getLong("goalsAgainst"),0L);

        if (tvGames  != null) tvGames.setText(String.valueOf(games));
        if (tvWins   != null) tvWins.setText(String.valueOf(wins));
        if (tvDraws  != null) tvDraws.setText(String.valueOf(draws));
        if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
        if (tvGF     != null) tvGF.setText(String.valueOf(gf));
        if (tvGA     != null) tvGA.setText(String.valueOf(ga));
        if (tvWinRate != null)
            tvWinRate.setText(games > 0 ? Math.round((wins * 100f) / games) + "%" : "-");
        if (recordSection != null) recordSection.setVisibility(View.VISIBLE);
    }

    // ── UI 바인딩 — 다음 일정 ────────────────────────────────────────────────────

    private void bindNextSchedule(@Nullable DocumentSnapshot doc) {
        if (!isAdded() || nextScheduleCard == null) return;

        if (doc == null || !doc.exists()) {
            nextScheduleCard.setVisibility(View.GONE);
            showNoScheduleMessage();
            return;
        }

        nextScheduleCard.setVisibility(View.VISIBLE);
        if (nextScheduleContainer != null) nextScheduleContainer.removeAllViews();

        String date         = doc.getString("date");
        String time         = doc.getString("time");
        String homeTeamName = doc.getString("homeTeamName");
        String awayTeamName = doc.getString("awayTeamName");
        String homeLogo     = doc.getString("homeTeamLogoUrl");
        String awayLogo     = doc.getString("awayTeamLogoUrl");
        String stadiumName  = AppUtils.firstNonEmpty(
                doc.getString("stadiumName"), doc.getString("stadium"));
        String address      = AppUtils.firstNonEmpty(
                doc.getString("stadiumAddress"), doc.getString("address"));

        if (tvNextDateChip != null) {
            String dateDisplay = AppUtils.isEmpty(date) ? "" : DateUtils.appendWeekday(date);
            String timeDisplay = AppUtils.isEmpty(time) ? "" : " " + time;
            tvNextDateChip.setText(dateDisplay + timeDisplay);
        }
        if (tvHomeName != null) tvHomeName.setText(AppUtils.safe(homeTeamName));
        if (tvAwayName != null) tvAwayName.setText(AppUtils.safe(awayTeamName));
        if (tvPlace    != null) tvPlace.setText(AppUtils.safe(stadiumName));
        if (tvAddress  != null) tvAddress.setText(AppUtils.safe(address));

        if (imgHomeLogo != null && !AppUtils.isEmpty(homeLogo))
            Glide.with(this).load(homeLogo).placeholder(R.drawable.ic_shield_gray).into(imgHomeLogo);
        if (imgAwayLogo != null && !AppUtils.isEmpty(awayLogo))
            Glide.with(this).load(awayLogo).placeholder(R.drawable.ic_shield_gray).into(imgAwayLogo);
    }

    private void showNoScheduleMessage() {
        if (nextScheduleContainer == null) return;
        nextScheduleContainer.removeAllViews();
        TextView msg = new TextView(requireContext());
        msg.setText("예정된 일정이 없습니다.");
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

    // ── UI 바인딩 — 멤버 목록 ────────────────────────────────────────────────────

    private void bindMemberList(List<DocumentSnapshot> profiles) {
        if (!isAdded()) return;
        RecyclerView recyclerView = getView() != null
                ? getView().findViewById(R.id.recyclerViewMembers) : null;
        if (recyclerView == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = user != null ? user.getUid() : "";

        List<DocumentSnapshot> fwDocs = new ArrayList<>(), mfDocs = new ArrayList<>(),
                dfDocs = new ArrayList<>(), gkDocs = new ArrayList<>();

        for (DocumentSnapshot p : profiles) {
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

        if (tvMemberTitle != null) tvMemberTitle.setText("팀 멤버 (" + profiles.size() + ")");

        final String cap  = captainUid;
        final String vice = viceCaptainUid;

        TeamMemberAdapter adapter = new TeamMemberAdapter(cap, vice, currentUid,
                (nickname, memberUid) ->
                        FirebaseFirestore.getInstance()
                                .collection("teams").document(teamId).get()
                                .addOnSuccessListener(s -> {
                                    String latestVice = s != null
                                            ? AppUtils.safe(s.getString("viceCaptainUID")) : vice;
                                    showPlayerOptionsDialog(nickname, memberUid,
                                            teamId, latestVice, new TextView(requireContext()));
                                }));

        GridLayoutManager lm = new GridLayoutManager(requireContext(), 2);
        lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) {
                return adapter.getItemViewType(pos) == TeamMemberAdapter.TYPE_HEADER ? 2 : 1;
            }
        });
        recyclerView.setLayoutManager(lm);
        adapter.setItems(items);
        recyclerView.setAdapter(adapter);
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

    // ── 사용자 액션 다이얼로그 ────────────────────────────────────────────────────

    /**
     * ✅ 수정: dialog_invite 레이아웃 없음 → 코드로 직접 생성
     * 원본 SoccerClub 방식 그대로 복원
     */
    private void showInviteDialog() {
        if (!isAdded()) return;

        // 레이아웃 파일 없이 코드로 직접 EditText 생성
        EditText editNickname = new EditText(requireContext());
        editNickname.setHint("닉네임 입력");
        int pad = dp(16);
        editNickname.setPadding(pad, pad / 2, pad, pad / 2);

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("팀원 초대")
                .setView(editNickname)
                .setPositiveButton("초대 보내기", null) // show 이후 처리
                .setNegativeButton("취소", null)
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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
                        .addOnFailureListener(e ->
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
                ? senderUid + "_" + receiverUid
                : receiverUid + "_" + senderUid;

        long now = System.currentTimeMillis();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference roomRef =
                db.collection("chatRooms").document(roomId);

        Map<String, Object> roomData = new HashMap<>();
        roomData.put("participants",  Arrays.asList(senderUid, receiverUid));
        roomData.put("lastMessage",   "[팀 초대] 우리 팀에 합류해보세요!");
        roomData.put("lastTimestamp", now);
        if (!AppUtils.isEmpty(teamId)) roomData.put("teamId", teamId);

        roomRef.set(roomData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Map<String, Object> message = new HashMap<>();
                    message.put("senderId",    senderUid);
                    message.put("content",     "[팀 초대] 우리 팀에 합류해보세요!");
                    message.put("messageType", "team_invite");
                    message.put("teamId",      teamId);
                    message.put("timestamp",   now);

                    roomRef.collection("messages").add(message)
                            .addOnSuccessListener(d -> {
                                if (isAdded())
                                    CustomToast.success(requireContext(), "초대 메시지를 보냈어요.");
                            });
                });
    }

    private void showLeaveTeamDialog(String currentUid) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("팀 탈퇴")
                .setMessage("정말로 팀에서 탈퇴하시겠습니까?")
                .setPositiveButton("탈퇴", (d, i) -> leaveTeam(currentUid))
                .setNegativeButton("취소", null)
                .show();
    }

    private void leaveTeam(String currentUid) {
        if (AppUtils.isEmpty(teamId) || !isAdded()) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("teams").document(teamId)
                .update("members", FieldValue.arrayRemove(currentUid))
                .addOnSuccessListener(v ->
                        db.collection("profiles").document(currentUid)
                                .update("myTeam", "")
                                .addOnSuccessListener(v2 -> {
                                    if (!isAdded()) return;
                                    CustomToast.success(requireContext(), "팀에서 탈퇴했습니다.");
                                    teamId = null;
                                    if (state != null) {
                                        state.setEmptyMessage("소속된 팀이 없습니다.\n팀에 가입하거나 팀을 생성하세요.");
                                        state.showEmpty();
                                    }
                                }))
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    CustomToast.error(requireContext(), "탈퇴에 실패했어요. 다시 시도해 주세요.");
                });
    }

    private void showPlayerOptionsDialog(String nickname, String memberUid,
                                         String teamId, String latestVice,
                                         TextView playerViceTag) {
        if (!isAdded()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.getUid().equals(captainUid)) return;
        String currentUid = user.getUid();

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_player_options, null);
        TextView     txtName       = dialogView.findViewById(R.id.txtPlayerName);
        LinearLayout btnAssignVice = dialogView.findViewById(R.id.btnAssignVice);
        LinearLayout btnKickPlayer = dialogView.findViewById(R.id.btnKickPlayer);
        LinearLayout btnAssignCap  = dialogView.findViewById(R.id.btnAssignCaptain);
        TextView     viceText      = dialogView.findViewById(R.id.txtAssignViceText);

        if (txtName != null) txtName.setText(nickname);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView).create();

        if (btnAssignCap != null) {
            btnAssignCap.setVisibility(View.VISIBLE);
            btnAssignCap.setOnClickListener(v -> {
                dialog.dismiss();
                if (currentUid.equals(memberUid)) {
                    CustomToast.warning(requireContext(), "자기 자신에게는 위임할 수 없어요."); return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("주장 권한 위임")
                        .setMessage(nickname + " 님에게 주장 권한을 위임하시겠습니까?")
                        .setPositiveButton("위임", (d, i) ->
                                FirebaseFirestore.getInstance()
                                        .collection("teams").document(teamId)
                                        .update("captainUID", memberUid)
                                        .addOnSuccessListener(v2 -> {
                                            if (!isAdded()) return;
                                            captainUid = memberUid;
                                            CustomToast.success(requireContext(), "주장 권한이 위임됐어요.");
                                        })
                                        .addOnFailureListener(e ->
                                                CustomToast.error(requireContext(), "위임에 실패했어요.")))
                        .setNegativeButton("취소", null).show();
            });
        }

        if (btnAssignVice != null) {
            boolean isCurrentVice = memberUid.equals(latestVice);
            if (viceText != null) viceText.setText(isCurrentVice ? "부주장 해제" : "부주장 지정");
            btnAssignVice.setOnClickListener(v -> {
                dialog.dismiss();
                String newVice = isCurrentVice ? "" : memberUid;
                FirebaseFirestore.getInstance()
                        .collection("teams").document(teamId)
                        .update("viceCaptainUID", newVice)
                        .addOnSuccessListener(v2 -> {
                            if (!isAdded()) return;
                            viceCaptainUid = newVice;
                            CustomToast.success(requireContext(),
                                    isCurrentVice ? "부주장이 해제됐어요." : "부주장으로 지정됐어요.");
                        })
                        .addOnFailureListener(e ->
                                CustomToast.error(requireContext(), "변경에 실패했어요."));
            });
        }

        if (btnKickPlayer != null) {
            btnKickPlayer.setOnClickListener(v -> {
                dialog.dismiss();
                if (currentUid.equals(memberUid)) {
                    CustomToast.warning(requireContext(), "자기 자신은 강퇴할 수 없어요."); return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("팀원 강퇴")
                        .setMessage(nickname + " 님을 팀에서 강퇴하시겠습니까?")
                        .setPositiveButton("강퇴", (d2, i) -> kickMember(memberUid, nickname))
                        .setNegativeButton("취소", null).show();
            });
        }

        dialog.show();
    }

    private void kickMember(String memberUid, String nickname) {
        if (AppUtils.isEmpty(teamId) || !isAdded()) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("teams").document(teamId)
                .update("members", FieldValue.arrayRemove(memberUid))
                .addOnSuccessListener(v -> {
                    db.collection("profiles").document(memberUid).update("myTeam", "");
                    if (!isAdded()) return;
                    CustomToast.success(requireContext(), nickname + " 님을 강퇴했어요.");
                    if (viewModel != null) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) viewModel.reload(user.getUid());
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    CustomToast.error(requireContext(), "강퇴에 실패했어요.");
                });
    }

    // ── 팀 사진 업로드 ────────────────────────────────────────────────────────────

    private void uploadTeamPhotoToFirebase(Uri imageUri) {
        if (AppUtils.isEmpty(teamId) || imageUri == null || !isAdded()) return;
        if (teamPhoto != null) teamPhoto.setEnabled(false);
        CustomToast.info(requireContext(), "업로드 중...");

        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(imageUri);
                if (is == null) throw new IOException("InputStream null");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int len;
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
                                            });
                                }))
                        .addOnFailureListener(e -> {
                            if (!isAdded()) return;
                            if (teamPhoto != null) teamPhoto.setEnabled(true);
                            CustomToast.error(requireContext(), "업로드에 실패했어요.");
                        });
            } catch (Exception e) {
                if (!isAdded()) return;
                if (teamPhoto != null) teamPhoto.setEnabled(true);
                CustomToast.error(requireContext(), "파일을 읽지 못했어요.");
            }
        });
    }

    // ── 자동 새로고침 ─────────────────────────────────────────────────────────────

    private void startUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
        uiHandler.postDelayed(upcomingRefreshRunnable, 30_000);
    }

    private void stopUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private void setTextOrGone(TextView tv, String value) {
        if (tv == null) return;
        if (AppUtils.isEmpty(value)) tv.setVisibility(View.GONE);
        else { tv.setVisibility(View.VISIBLE); tv.setText(value); }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}