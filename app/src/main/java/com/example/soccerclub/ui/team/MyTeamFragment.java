package com.example.soccerclub.ui.team;

import android.app.ProgressDialog;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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

public class MyTeamFragment extends Fragment {

    private StateLayout state;
    private ImageView teamLogo, teamPhoto, introToggle;
    private TextView teamName, teamIntro, teamRegion, teamSkill, teamAge;
    private TextView teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;
    private Button btnInvite, btnLeaveTeam;
    private LinearLayout playerListLayout, recordSection, nextScheduleContainer;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate, tvSeeDetails;
    private TextView tvMemberTitle, btnSeeAllSchedule;
    private View nextScheduleCard, scheduleContent, scheduleLoading;
    private TextView tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView imgHomeLogo, imgAwayLogo;

    private String teamId = null;
    private String introFullText = "";
    private boolean isIntroExpanded = false;
    private boolean firstImageDrawn = false;

    private ListenerRegistration recordListener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private static final int REQUEST_SELECT_TEAM_PHOTO = 101;
    private Uri selectedTeamPhotoUri;

    private final Runnable upcomingRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (isAdded() && !AppUtils.isEmpty(teamId)) {
                loadUpcomingSchedule(teamId);
                uiHandler.postDelayed(this, 30_000);
            }
        }
    };

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

        teamPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_SELECT_TEAM_PHOTO);
        });

        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("profiles").document(currentUid)
                .get()
                .addOnSuccessListener(doc -> {
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
                    CustomToast.error(requireContext(), "프로필을 불러오지 못했어요.");
                    state.showEmpty();
                });

        tvSeeDetails.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), RecordsActivity.class);
            intent.putExtra("myTeamId", teamId);
            startActivity(intent);
        });

        btnSeeAllSchedule.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ScheduleActivity.class);
            startActivity(intent);
        });

        btnLeaveTeam.setOnClickListener(v -> onClickLeaveTeam());

        return view;
    }

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
        if (recordListener != null) { recordListener.remove(); recordListener = null; }
        super.onDestroyView();
    }

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
                    state.showEmpty();
                });
    }

    private void bindTeamTextFields(DocumentSnapshot doc) {
        String teamNameStr     = doc.getString("teamName");
        String intro           = doc.getString("intro");
        String region          = doc.getString("region");
        String skill           = doc.getString("skill");
        String ageRange        = doc.getString("ageRange");
        String activityDayVal  = doc.getString("activityDay");
        String homeStadiumName = doc.getString("homeStadiumName");
        String stadiumAddr     = doc.getString("stadium");
        String timeStart       = doc.getString("timeStart");
        String timeEnd         = doc.getString("timeEnd");
        String captainUid      = doc.getString("captainUID");
        String viceCaptainUid  = doc.getString("viceCaptainUID");
        String currentUid      = FirebaseAuth.getInstance().getCurrentUser().getUid();

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

        setTextOrGone(teamActivityDay, activityDisplay);
        setTextOrGone(teamHomeStadiumName, homeStadiumName);
        setTextOrGone(teamHomeStadiumAddress, stadiumAddr);

        boolean isLeader = currentUid.equals(captainUid) || currentUid.equals(viceCaptainUid);
        btnInvite.setVisibility(isLeader ? View.VISIBLE : View.GONE);
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
                        @Override public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t,
                                boolean b) {
                            if (!firstImageDrawn && isAdded()) { firstImageDrawn = true; state.showContent(); }
                            return false;
                        }
                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable r,
                                Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t,
                                com.bumptech.glide.load.DataSource ds, boolean b) {
                            if (!firstImageDrawn && isAdded()) { firstImageDrawn = true; state.showContent(); }
                            return false;
                        }
                    }).into(teamPhoto);
        } else {
            teamPhoto.setImageResource(R.drawable.default_team_photo);
        }

        if (!AppUtils.isEmpty(logoUrl)) {
            Glide.with(requireContext()).load(logoUrl).apply(opts)
                    .placeholder(R.drawable.ic_shield_gray).into(teamLogo);
        } else {
            teamLogo.setImageResource(R.drawable.ic_shield_gray);
        }
    }

    private void loadPlayerList(String teamId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("teams").document(teamId).get()
                .addOnSuccessListener(teamDoc -> {
                    if (!isAdded() || !teamDoc.exists()) return;
                    String captainUid     = teamDoc.getString("captainUID");
                    String viceCaptainUid = teamDoc.getString("viceCaptainUID");
                    List<String> members  = (List<String>) teamDoc.get("members");
                    String currentUid     = FirebaseAuth.getInstance().getCurrentUser().getUid();
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

                                TeamMemberAdapter adapter = new TeamMemberAdapter(cap, vice, currentUid,
                                        (nickname, uid) ->
                                                db.collection("teams").document(teamId).get()
                                                        .addOnSuccessListener(s -> {
                                                            String latestVice = s != null
                                                                    ? s.getString("viceCaptainUID") : vice;
                                                            showPlayerOptionsDialog(nickname, uid, teamId,
                                                                    latestVice, new TextView(requireContext()));
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
                            });
                });
    }

    private void addPositionGroup(List<TeamMemberAdapter.MemberItem> items,
                                   String header, List<DocumentSnapshot> docs) {
        TeamMemberAdapter.MemberItem h = new TeamMemberAdapter.MemberItem();
        h.type = TeamMemberAdapter.TYPE_HEADER;
        h.header = header;
        items.add(h);
        for (DocumentSnapshot d : docs) {
            TeamMemberAdapter.MemberItem item = new TeamMemberAdapter.MemberItem();
            item.type = TeamMemberAdapter.TYPE_PLAYER;
            item.uid = d.getId();
            item.nickname = d.getString("nickname");
            item.photoUrl = d.getString("profileImageUrl");
            items.add(item);
        }
    }

    private void bindRecordSummary(String teamId) {
        if (recordListener != null) { recordListener.remove(); }
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

                    tvGames.setText(String.valueOf(games));
                    tvWins.setText(String.valueOf(wins));
                    tvDraws.setText(String.valueOf(draws));
                    tvLosses.setText(String.valueOf(losses));
                    tvGF.setText(String.valueOf(gf));
                    tvGA.setText(String.valueOf(ga));

                    String winRate = games > 0
                            ? Math.round((wins * 100f) / games) + "%" : "-";
                    if (tvWinRate != null) tvWinRate.setText(winRate);
                });
    }

    private void loadUpcomingSchedule(String teamId) {
        if (nextScheduleCard == null || !isAdded()) return;
        showScheduleLoading(true);
        long now = System.currentTimeMillis();

        FirebaseFirestore.getInstance()
                .collection("schedules").document(teamId)
                .collection("events")
                .get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    DocumentSnapshot upcoming = null;
                    long minTs = Long.MAX_VALUE;

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Long matchTs = d.getLong("matchTs");
                        Long endTs   = d.getLong("endTs");
                        long end     = endTs != null ? AppUtils.normalizeToMillis(endTs)
                                : (matchTs != null ? AppUtils.normalizeToMillis(matchTs) + 2 * 3600 * 1000L : 0);
                        if (end > now && matchTs != null) {
                            long ts = AppUtils.normalizeToMillis(matchTs);
                            if (ts < minTs) { minTs = ts; upcoming = d; }
                        }
                    }

                    if (upcoming != null) renderNextSchedule(upcoming);
                    else renderNextScheduleEmpty();
                    showScheduleLoading(false);
                });
    }

    private void renderNextSchedule(DocumentSnapshot doc) {
        if (nextScheduleCard == null) return;
        nextScheduleCard.setVisibility(View.VISIBLE);

        String date       = AppUtils.safe(doc.getString("date"));
        String time       = AppUtils.safe(doc.getString("time"));
        String opponent   = AppUtils.safe(doc.getString("opponentTeamName"));
        String stadium    = AppUtils.firstNonEmpty(doc.getString("stadiumName"), doc.getString("stadium"));
        String address    = AppUtils.safe(doc.getString("address"));
        String myTeamName = AppUtils.safe(doc.getString("homeTeamName"));

        if (tvNextDateChip != null) tvNextDateChip.setText(date + (!AppUtils.isEmpty(time) ? " · " + time : ""));
        if (tvHomeName != null)     tvHomeName.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
        if (tvAwayName != null)     tvAwayName.setText(AppUtils.isEmpty(opponent) ? "-" : opponent);
        if (tvPlace != null)        tvPlace.setText(AppUtils.nz(stadium, "-"));
        if (tvAddress != null)      tvAddress.setText(address);

        String homeLogoUrl = AppUtils.safe(doc.getString("homeTeamLogoUrl"));
        String awayLogoUrl = AppUtils.safe(doc.getString("opponentLogoUrl"));
        if (imgHomeLogo != null) Glide.with(requireContext()).load(homeLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgHomeLogo);
        if (imgAwayLogo != null) Glide.with(requireContext()).load(awayLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);

        if (scheduleContent != null) scheduleContent.setVisibility(View.VISIBLE);
    }

    private void renderNextScheduleEmpty() {
        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
        if (nextScheduleContainer == null) return;

        View old = nextScheduleContainer.findViewWithTag("emptyUpcomingMsg");
        if (old != null) nextScheduleContainer.removeView(old);

        TextView msg = new TextView(getContext());
        msg.setTag("emptyUpcomingMsg");
        msg.setText("다가오는 일정이 없습니다.");
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

    private void uploadTeamPhotoToFirebase(Uri imageUri) {
        if (AppUtils.isEmpty(teamId) || imageUri == null) return;
        ProgressDialog pd = new ProgressDialog(getContext());
        pd.setMessage("업로드 중...");
        pd.show();

        try {
            InputStream is = requireContext().getContentResolver().openInputStream(imageUri);
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
                                pd.dismiss();
                                FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                        .update("teamPhotoUrl", uri.toString())
                                        .addOnSuccessListener(v ->
                                                CustomToast.success(requireContext(), "팀 사진이 변경됐어요."));
                            }))
                    .addOnFailureListener(e -> { pd.dismiss();
                        CustomToast.error(requireContext(), "업로드 실패: " + e.getMessage()); });
        } catch (IOException e) {
            pd.dismiss();
            CustomToast.error(requireContext(), "이미지 처리 실패: " + e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_TEAM_PHOTO
                && resultCode == requireActivity().RESULT_OK && data != null) {
            selectedTeamPhotoUri = data.getData();
            if (selectedTeamPhotoUri != null) {
                teamPhoto.setImageURI(selectedTeamPhotoUri);
                uploadTeamPhotoToFirebase(selectedTeamPhotoUri);
            }
        }
    }

    private void onClickLeaveTeam() {
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        new AlertDialog.Builder(getContext())
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
                                db.collection("teams").document(tid).get()
                                        .addOnSuccessListener(teamSnap -> {
                                            String captainUid = teamSnap.getString("captainUID");
                                            if (currentUid.equals(captainUid)) {
                                                CustomToast.warning(requireContext(),
                                                        "팀장은 탈퇴할 수 없어요.\n먼저 '주장 위임'을 해 주세요.");
                                                return;
                                            }
                                            List<String> members = (List<String>) teamSnap.get("members");
                                            if (members != null) members.remove(currentUid);

                                            Map<String, Object> updates = new HashMap<>();
                                            updates.put("members", members);
                                            if (currentUid.equals(teamSnap.getString("viceCaptainUID")))
                                                updates.put("viceCaptainUID", "");

                                            db.collection("teams").document(tid).update(updates)
                                                    .addOnSuccessListener(v ->
                                                            db.collection("profiles").document(currentUid)
                                                                    .update("myTeam", null)
                                                                    .addOnSuccessListener(v2 ->
                                                                            CustomToast.success(requireContext(), "팀에서 탈퇴했어요.")));
                                        });
                            });
                })
                .setNegativeButton("아니오", null)
                .show();
    }

    private void showPlayerOptionsDialog(String nickname, String uid, String teamId,
                                          String viceCaptainUid, TextView playerViceTag) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_player_options, null);
        TextView txtName         = dialogView.findViewById(R.id.txtPlayerName);
        LinearLayout btnAssignVice    = dialogView.findViewById(R.id.btnAssignVice);
        LinearLayout btnKickPlayer    = dialogView.findViewById(R.id.btnKickPlayer);
        LinearLayout btnAssignCaptain = dialogView.findViewById(R.id.btnAssignCaptain);
        TextView assignViceText  = dialogView.findViewById(R.id.txtAssignViceText);

        txtName.setText(nickname);

        AlertDialog dialog = new AlertDialog.Builder(getContext(), R.style.CustomDialog)
                .setView(dialogView).create();

        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        btnAssignCaptain.setVisibility(View.VISIBLE);
        btnAssignCaptain.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentUid.equals(uid)) {
                CustomToast.warning(requireContext(), "자기 자신에게는 위임할 수 없어요.");
                return;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle("주장 권한 위임")
                    .setMessage(nickname + " 님에게 주장 권한을 위임하시겠습니까?")
                    .setPositiveButton("예", (d, i) ->
                            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                                    .update("captainUID", uid)
                                    .addOnSuccessListener(v2 ->
                                            CustomToast.success(requireContext(), "주장 권한이 위임됐어요.")))
                    .setNegativeButton("아니오", null).show();
        });

        boolean isVice = uid.equals(viceCaptainUid);
        if (assignViceText != null)
            assignViceText.setText(isVice ? "부주장 해제" : "부주장 지정");
        btnAssignVice.setOnClickListener(v -> {
            dialog.dismiss();
            String newVice = isVice ? "" : uid;
            FirebaseFirestore.getInstance().collection("teams").document(teamId)
                    .update("viceCaptainUID", newVice)
                    .addOnSuccessListener(v2 ->
                            CustomToast.success(requireContext(),
                                    isVice ? "부주장이 해제됐어요." : "부주장으로 지정됐어요."));
        });

        btnKickPlayer.setOnClickListener(v -> {
            dialog.dismiss();
            if (currentUid.equals(uid)) {
                CustomToast.warning(requireContext(), "자기 자신을 강퇴할 수 없어요.");
                return;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle("팀원 강퇴")
                    .setMessage(nickname + " 님을 강퇴하시겠습니까?")
                    .setPositiveButton("예", (d, i) -> {
                        FirebaseFirestore db = FirebaseFirestore.getInstance();
                        db.collection("teams").document(teamId).get()
                                .addOnSuccessListener(teamSnap -> {
                                    List<String> members = (List<String>) teamSnap.get("members");
                                    if (members != null) members.remove(uid);
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("members", members);
                                    if (uid.equals(teamSnap.getString("viceCaptainUID")))
                                        updates.put("viceCaptainUID", "");
                                    db.collection("teams").document(teamId).update(updates)
                                            .addOnSuccessListener(v2 ->
                                                    db.collection("profiles").document(uid)
                                                            .update("myTeam", null)
                                                            .addOnSuccessListener(v3 ->
                                                                    CustomToast.success(requireContext(),
                                                                            nickname + " 님이 강퇴됐어요.")));
                                });
                    })
                    .setNegativeButton("아니오", null).show();
        });

        dialog.show();
    }

    private void startUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
        uiHandler.postDelayed(upcomingRefreshRunnable, 30_000);
    }

    private void stopUpcomingAutoRefresh() {
        uiHandler.removeCallbacks(upcomingRefreshRunnable);
    }

    private void setTextOrGone(TextView tv, String value) {
        if (tv == null) return;
        if (AppUtils.isEmpty(value)) { tv.setVisibility(View.GONE); }
        else { tv.setVisibility(View.VISIBLE); tv.setText(value); }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}