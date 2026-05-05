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
import com.example.soccerclub.model.ChatRoomItem;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.util.GlideHelper;

import java.util.List;

public class ChatRoomItemAdapter extends RecyclerView.Adapter<ChatRoomItemAdapter.ViewHolder> {

    private final List<ChatRoomItem> chatRooms;
    private final Context context;
    private final OnChatClickListener listener;

    public interface OnChatClickListener {
        void onClick(ChatRoomItem item);
    }

    public ChatRoomItemAdapter(List<ChatRoomItem> chatRooms, Context context,
                               OnChatClickListener listener) {
        this.chatRooms = chatRooms;
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        ChatRoomItem item = chatRooms.get(position);
        return item.getRoomId() != null ? item.getRoomId().hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatRoomItem item = chatRooms.get(position);

        holder.nickname.setText(
                AppUtils.isEmpty(item.getPeerNickname()) ? "(알 수 없음)" : item.getPeerNickname()
        );
        holder.lastMessage.setText(AppUtils.safe(item.getLastMessage()));
        holder.timestamp.setText(
                item.getLastTimestamp() > 0
                        ? DateUtils.formatRelativeTime(item.getLastTimestamp())
                        : ""
        );

        if (item.getUnreadCount() > 0) {
            holder.unreadCount.setVisibility(View.VISIBLE);
            holder.unreadCount.setText(String.valueOf(item.getUnreadCount()));
        } else {
            holder.unreadCount.setVisibility(View.GONE);
        }

        GlideHelper.loadProfile(context, item.getPeerProfileImage(), holder.profileImage);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() { return chatRooms.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView nickname, lastMessage, timestamp, unreadCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profileImage);
            nickname     = itemView.findViewById(R.id.nickname);
            lastMessage  = itemView.findViewById(R.id.lastMessage);
            timestamp    = itemView.findViewById(R.id.timestamp);
            unreadCount  = itemView.findViewById(R.id.unreadCount);
        }
    }
}