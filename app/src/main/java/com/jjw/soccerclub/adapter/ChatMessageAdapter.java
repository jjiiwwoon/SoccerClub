package com.jjw.soccerclub.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── 메시지 모델 ───────────────────────────────────────────────────────────────

    public static class ChatMessage {
        public String senderId;
        public String text;
        public long   timestamp;
        public String messageType;   // "text" or "team_invite"
        public String teamId;        // 초대한 팀 ID
        public String inviteStatus;  // ✅ "accepted" / "rejected" / null
        public String messageDocId;  // ✅ Firestore 문서 ID (상태 업데이트용)

        public String getSenderId()    { return senderId; }
        public String getText()        { return text; }
        public long   getTimestamp()   { return timestamp; }
        public String getMessageType() { return messageType; }
        public String getTeamId()      { return teamId; }
        public String getInviteStatus(){ return inviteStatus; }
    }

    // ── ViewType 상수 ─────────────────────────────────────────────────────────────

    private static final int TYPE_MY_TEXT      = 1;
    private static final int TYPE_OTHER_TEXT   = 0;
    private static final int TYPE_TEAM_INVITE  = 2; // ✅ 팀 초대 메시지

    // ── 필드 ─────────────────────────────────────────────────────────────────────

    private final List<ChatMessage> messages;
    private final String myUid;
    private final String roomId;
    private final Context context;

    private String myTeamId = null;
    public void setMyTeamId(String myTeamId) { this.myTeamId = myTeamId; }

    private final Map<String, ProfileLite> profileCache = new HashMap<>();

    private static class ProfileLite {
        String nickname;
        String imageUrl;
        ProfileLite(String n, String u) { nickname = n; imageUrl = u; }
    }

    public ChatMessageAdapter(List<ChatMessage> messages, String myUid,
                              String roomId, Context context) {
        this.messages = messages;
        this.myUid    = myUid;
        this.roomId   = roomId;
        this.context  = context;
    }

    // ── ViewType 결정 ─────────────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if ("team_invite".equals(msg.getMessageType())) {
            // ✅ 초대 보낸 사람 → 일반 내 메시지처럼 오른쪽 표시 (버튼 없음)
            // ✅ 초대 받은 사람 → 수락/거절 버튼 있는 카드 표시
            if (myUid.equals(msg.getSenderId())) {
                return TYPE_MY_TEXT;
            } else {
                return TYPE_TEAM_INVITE;
            }
        }
        return msg.getSenderId().equals(myUid) ? TYPE_MY_TEXT : TYPE_OTHER_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_MY_TEXT:
                return new MyMsgVH(inf.inflate(R.layout.chat_message_item_right, parent, false));
            case TYPE_TEAM_INVITE:
                return new InviteVH(inf.inflate(R.layout.chat_message_item_invite, parent, false));
            default:
                return new OtherMsgVH(inf.inflate(R.layout.chat_message_item_left, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String timeStr = msg.getTimestamp() > 0
                ? DateUtils.formatRelativeTime(msg.getTimestamp()) : "";

        if (holder instanceof MyMsgVH) {
            MyMsgVH h = (MyMsgVH) holder;
            // ✅ team_invite를 보낸 입장이면 초대 텍스트로 표시
            String display = "team_invite".equals(msg.getMessageType())
                    ? AppUtils.firstNonEmpty(msg.getText(), "[팀 초대] 우리 팀에 합류해보세요!")
                    : AppUtils.safe(msg.getText());
            h.tvMessage.setText(display);
            h.tvTime.setText(timeStr);

        } else if (holder instanceof InviteVH) {
            InviteVH h = (InviteVH) holder;
            h.tvMessage.setText("[팀 초대] 우리 팀에 합류해보세요!");
            h.tvTime.setText(timeStr);

            final ChatMessage finalMsg = msg;
            String inviteStatus = msg.getInviteStatus();

            if ("accepted".equals(inviteStatus)) {
                // ✅ 수락 완료 — 버튼 숨기고 "수락 완료" 텍스트 표시
                h.btnAccept.setVisibility(View.GONE);
                h.btnReject.setVisibility(View.GONE);
                h.tvStatus.setText("✓ 수락 완료");
                h.tvStatus.setVisibility(View.VISIBLE);
            } else if ("rejected".equals(inviteStatus)) {
                // ✅ 거절 완료 — 버튼 숨기고 "거절함" 텍스트 표시
                h.btnAccept.setVisibility(View.GONE);
                h.btnReject.setVisibility(View.GONE);
                h.tvStatus.setText("✗ 거절함");
                h.tvStatus.setVisibility(View.VISIBLE);
            } else {
                // ✅ 아직 응답 안 함 — 수락/거절 버튼 표시
                h.tvStatus.setVisibility(View.GONE);
                h.btnAccept.setVisibility(View.VISIBLE);
                h.btnReject.setVisibility(View.VISIBLE);
                h.btnAccept.setEnabled(true);
                h.btnReject.setEnabled(true);

                h.btnAccept.setOnClickListener(v -> {
                    h.btnAccept.setEnabled(false);
                    h.btnReject.setEnabled(false);
                    acceptInvite(finalMsg, h);
                });
                h.btnReject.setOnClickListener(v -> {
                    h.btnAccept.setEnabled(false);
                    h.btnReject.setEnabled(false);
                    rejectInvite(finalMsg, h);
                });
            }

        } else if (holder instanceof OtherMsgVH) {
            OtherMsgVH h = (OtherMsgVH) holder;
            // team_invite를 보낸 사람 입장에서는 일반 텍스트처럼 표시
            String display = "team_invite".equals(msg.getMessageType())
                    ? "[팀 초대] 우리 팀에 합류해보세요!" : AppUtils.safe(msg.getText());
            h.tvMessage.setText(display);
            h.tvTime.setText(timeStr);
            loadProfile(msg.getSenderId(), h.ivProfile);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ── 팀 초대 수락 ──────────────────────────────────────────────────────────────

    private void acceptInvite(ChatMessage msg, InviteVH h) {
        String inviteTeamId = msg.getTeamId();
        if (AppUtils.isEmpty(inviteTeamId)) {
            // teamId가 없으면 채팅방 문서에서 읽기
            FirebaseFirestore.getInstance().collection("chatRooms").document(roomId).get()
                    .addOnSuccessListener(snap -> {
                        String tid = snap.getString("teamId");
                        if (!AppUtils.isEmpty(tid)) {
                            doAcceptInvite(msg, tid, h);
                        } else {
                            CustomToast.error(context, "팀 정보를 찾을 수 없어요.");
                            h.btnAccept.setEnabled(true);
                            h.btnReject.setEnabled(true);
                        }
                    });
        } else {
            doAcceptInvite(msg, inviteTeamId, h);
        }
    }

    // ✅ 즉시 가입 — runTransaction으로 skillAverage 원자적 처리
    private void doAcceptInvite(ChatMessage inviteMsg, String inviteTeamId, InviteVH h) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        com.google.firebase.firestore.DocumentReference teamRef    = db.collection("teams").document(inviteTeamId);
        com.google.firebase.firestore.DocumentReference profileRef = db.collection("profiles").document(myUid);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot teamSnap    = transaction.get(teamRef);
                    com.google.firebase.firestore.DocumentSnapshot profileSnap = transaction.get(profileRef);

                    java.util.List<String> members = (java.util.List<String>) teamSnap.get("members");
                    if (members != null && members.contains(myUid)) {
                        throw new RuntimeException("이미 팀원입니다.");
                    }

                    long skill = profileSnap.getLong("skill") != null
                            ? profileSnap.getLong("skill") : 0L;

                    transaction.update(teamRef, "members", com.google.firebase.firestore.FieldValue.arrayUnion(myUid));
                    transaction.update(teamRef, "memberCount", com.google.firebase.firestore.FieldValue.increment(1L));
                    transaction.update(teamRef, "skillSum",    com.google.firebase.firestore.FieldValue.increment(skill));

                    long curSum   = teamSnap.getLong("skillSum")    != null ? teamSnap.getLong("skillSum")    : 0L;
                    long curCount = teamSnap.getLong("memberCount") != null ? teamSnap.getLong("memberCount") : 0L;
                    long newSum   = curSum + skill;
                    long newCount = curCount + 1;
                    int  newAvg   = newCount > 0 ? (int)(newSum / newCount) : 0;
                    transaction.update(teamRef, "skillAverage", newAvg);
                    transaction.update(profileRef, "myTeam", inviteTeamId);

                    return null;
                })
                .addOnSuccessListener(v -> {
                    // ✅ inviteStatus 저장 (재진입 시 버튼 안 나오게)
                    if (!AppUtils.isEmpty(inviteMsg.messageDocId)) {
                        FirebaseFirestore.getInstance()
                                .collection("chatRooms").document(roomId)
                                .collection("messages").document(inviteMsg.messageDocId)
                                .update("inviteStatus", "accepted");
                    }
                    // ✅ 로컬 상태 즉시 업데이트 → 초대카드 "수락 완료"로 변경
                    inviteMsg.inviteStatus = "accepted";
                    h.btnAccept.setVisibility(View.GONE);
                    h.btnReject.setVisibility(View.GONE);
                    h.tvStatus.setText("✓ 수락 완료");
                    h.tvStatus.setVisibility(View.VISIBLE);

                    // ✅ 수락 메시지 timestamp는 초대 메시지보다 반드시 크게 설정
                    long now = Math.max(System.currentTimeMillis(),
                            inviteMsg.timestamp > 0 ? inviteMsg.timestamp + 1 : System.currentTimeMillis());
                    java.util.Map<String, Object> resultMsg = new java.util.HashMap<>();
                    resultMsg.put("senderId",    myUid);
                    resultMsg.put("content",     "팀 초대를 수락했어요 🎉");
                    resultMsg.put("messageType", "text");
                    resultMsg.put("timestamp",   now);
                    FirebaseFirestore.getInstance()
                            .collection("chatRooms").document(roomId)
                            .collection("messages").add(resultMsg);

                    // lastMessage 업데이트
                    java.util.Map<String, Object> roomUpdate = new java.util.HashMap<>();
                    roomUpdate.put("lastMessage",   "팀 초대를 수락했어요 🎉");
                    roomUpdate.put("lastTimestamp", now);
                    FirebaseFirestore.getInstance()
                            .collection("chatRooms").document(roomId)
                            .set(roomUpdate, com.google.firebase.firestore.SetOptions.merge());

                    CustomToast.success(context, "팀에 합류했어요!");
                })
                .addOnFailureListener(e -> {
                    String errMsg = e.getMessage() != null ? e.getMessage() : "가입 실패";
                    CustomToast.error(context, errMsg);
                    h.btnAccept.setEnabled(true);
                    h.btnReject.setEnabled(true);
                });
    }

    // ── 팀 초대 거절 ──────────────────────────────────────────────────────────────

    private void rejectInvite(ChatMessage msg, InviteVH h) {
        if (!AppUtils.isEmpty(msg.messageDocId)) {
            FirebaseFirestore.getInstance()
                    .collection("chatRooms").document(roomId)
                    .collection("messages").document(msg.messageDocId)
                    .update("inviteStatus", "rejected");
        }
        // ✅ 로컬 상태 즉시 업데이트 → 초대카드 "거절함"으로 변경
        msg.inviteStatus = "rejected";
        h.btnAccept.setVisibility(View.GONE);
        h.btnReject.setVisibility(View.GONE);
        h.tvStatus.setText("✗ 거절함");
        h.tvStatus.setVisibility(View.VISIBLE);

        // ✅ 거절 메시지 timestamp는 초대 메시지보다 반드시 크게 설정
        long now = Math.max(System.currentTimeMillis(),
                msg.timestamp > 0 ? msg.timestamp + 1 : System.currentTimeMillis());
        java.util.Map<String, Object> resultMsg = new java.util.HashMap<>();
        resultMsg.put("senderId",    myUid);
        resultMsg.put("content",     "팀 초대를 거절했어요.");
        resultMsg.put("messageType", "text");
        resultMsg.put("timestamp",   now);
        FirebaseFirestore.getInstance()
                .collection("chatRooms").document(roomId)
                .collection("messages").add(resultMsg);

        java.util.Map<String, Object> roomUpdate = new java.util.HashMap<>();
        roomUpdate.put("lastMessage",   "팀 초대를 거절했어요.");
        roomUpdate.put("lastTimestamp", now);
        FirebaseFirestore.getInstance()
                .collection("chatRooms").document(roomId)
                .set(roomUpdate, com.google.firebase.firestore.SetOptions.merge());

        CustomToast.info(context, "초대를 거절했어요.");
    }

    // ── 프로필 로드 ───────────────────────────────────────────────────────────────

    private void loadProfile(String uid, ImageView ivProfile) {
        if (AppUtils.isEmpty(uid) || ivProfile == null) return;
        ProfileLite cached = profileCache.get(uid);
        if (cached != null) {
            GlideHelper.loadProfile(context, cached.imageUrl, ivProfile);
            return;
        }
        FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String nick = AppUtils.safe(doc.getString("nickname"));
                    String url  = doc.getString("profileImageUrl");
                    profileCache.put(uid, new ProfileLite(nick, url));
                    GlideHelper.loadProfile(context, url, ivProfile);
                });
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────────

    static class MyMsgVH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        MyMsgVH(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime    = v.findViewById(R.id.tvTime);
        }
    }

    static class OtherMsgVH extends RecyclerView.ViewHolder {
        TextView  tvMessage, tvTime;
        ImageView ivProfile;
        OtherMsgVH(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime    = v.findViewById(R.id.tvTime);
            ivProfile = v.findViewById(R.id.ivProfile);
        }
    }

    // ✅ 팀 초대 메시지 전용 ViewHolder
    static class InviteVH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvStatus;
        Button   btnAccept, btnReject;
        InviteVH(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime    = v.findViewById(R.id.tvTime);
            tvStatus  = v.findViewById(R.id.tvInviteStatus);
            btnAccept = v.findViewById(R.id.btnAcceptInvite);
            btnReject = v.findViewById(R.id.btnRejectInvite);
        }
    }
}