package com.example.soccerclub.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.util.GlideHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static class ChatMessage {
        public String senderId;
        public String text;
        public long timestamp;

        public String getSenderId() { return senderId; }
        public String getText()     { return text; }
        public long getTimestamp()  { return timestamp; }
    }

    private final List<ChatMessage> messages;
    private final String myUid;
    private final String roomId;
    private final Context context;

    private static final int TYPE_MY_TEXT    = 1;
    private static final int TYPE_OTHER_TEXT = 0;

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

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getSenderId().equals(myUid)
                ? TYPE_MY_TEXT : TYPE_OTHER_TEXT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MY_TEXT) {
            return new MyMsgVH(inf.inflate(R.layout.chat_message_item_right, parent, false));
        }
        return new OtherMsgVH(inf.inflate(R.layout.chat_message_item_left, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        String timeStr = msg.getTimestamp() > 0
                ? DateUtils.format(msg.getTimestamp(), "HH:mm") : "";

        if (holder instanceof MyMsgVH) {
            MyMsgVH h = (MyMsgVH) holder;
            h.tvMessage.setText(AppUtils.safe(msg.getText()));
            h.tvTime.setText(timeStr);
        } else if (holder instanceof OtherMsgVH) {
            OtherMsgVH h = (OtherMsgVH) holder;
            h.tvMessage.setText(AppUtils.safe(msg.getText()));
            h.tvTime.setText(timeStr);
            loadSenderProfile(msg.getSenderId(), h.tvNickname, h.ivProfile);
        }
    }

    private void loadSenderProfile(String senderId, TextView tvNickname, ImageView ivProfile) {
        if (AppUtils.isEmpty(senderId)) return;

        ProfileLite cached = profileCache.get(senderId);
        if (cached != null) {
            tvNickname.setText(AppUtils.safe(cached.nickname));
            GlideHelper.loadProfile(context, cached.imageUrl, ivProfile);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("profiles")
                .document(senderId)
                .get()
                .addOnSuccessListener(snap -> {
                    String nickname = AppUtils.nz(snap.getString("nickname"), "알 수 없음");
                    String imageUrl = snap.getString("profileImageUrl");
                    profileCache.put(senderId, new ProfileLite(nickname, imageUrl));
                    tvNickname.setText(nickname);
                    GlideHelper.loadProfile(context, imageUrl, ivProfile);
                });
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class MyMsgVH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        MyMsgVH(@NonNull View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvMessage);
            tvTime    = v.findViewById(R.id.tvTime);
        }
    }

    static class OtherMsgVH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvNickname;
        ImageView ivProfile;
        OtherMsgVH(@NonNull View v) {
            super(v);
            tvMessage  = v.findViewById(R.id.tvMessage);
            tvTime     = v.findViewById(R.id.tvTime);
            tvNickname = v.findViewById(R.id.tvNickname);
            ivProfile  = v.findViewById(R.id.ivProfile);
        }
    }
}