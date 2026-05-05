package com.example.soccerclub.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.List;

public class TeamMemberListAdapter extends RecyclerView.Adapter<TeamMemberListAdapter.VH> {

    public static class Item {
        public final String uid;
        public final String name;
        public final String position;
        public final String role;
        public final String photoUrl;
        public final int goals;

        public Item(String uid, String name, String position,
                    String role, String photoUrl, int goals) {
            this.uid      = uid;
            this.name     = name;
            this.position = position;
            this.role     = role;
            this.photoUrl = photoUrl;
            this.goals    = goals;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public void submit(List<Item> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item it = items.get(position);

        h.tvName.setText(TextUtils.isEmpty(it.name) ? "이름 없음" : it.name);
        h.tvPos.setText(TextUtils.isEmpty(it.position) ? "-" : it.position);
        h.tvPos.setTextColor(colorForPos(it.position));

        if (!TextUtils.isEmpty(it.role)) {
            h.tvRole.setVisibility(View.VISIBLE);
            h.tvRole.setText(it.role);
            h.tvRole.setTextColor("부주장".equals(it.role) ? 0xFF1565C0 : 0xFFB71C1C);
        } else {
            h.tvRole.setVisibility(View.GONE);
        }

        GlideHelper.loadProfile(h.ivPhoto.getContext(), it.photoUrl, h.ivPhoto);
    }

    @Override
    public int getItemCount() { return items.size(); }

    private int colorForPos(String pos) {
        if (pos == null) return 0xFF263238;
        switch (pos.toUpperCase()) {
            case "FW": return 0xFFD50000;
            case "MF": return 0xFF00C853;
            case "DF": return 0xFF2962FF;
            case "GK": return 0xFFFFD600;
            default:   return 0xFF263238;
        }
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView tvName, tvPos, tvRole;

        public VH(@NonNull View v) {
            super(v);
            ivPhoto = v.findViewById(R.id.ivPhoto);
            tvName  = v.findViewById(R.id.tvName);
            tvPos   = v.findViewById(R.id.tvPosition);
            tvRole  = v.findViewById(R.id.tvRole);
        }
    }
}