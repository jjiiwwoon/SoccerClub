package com.jjw.soccerclub.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.model.Team;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.List;

public class TeamAdapter extends RecyclerView.Adapter<TeamAdapter.TeamViewHolder> {

    private final Context       context;
    private final List<Team>    teamList;

    public interface OnItemClickListener {
        void onItemClick(Team team);
    }
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public TeamAdapter(Context context, List<Team> teamList) {
        this.context  = context;
        this.teamList = teamList != null ? teamList : new ArrayList<>();
    }

    public void updateList(List<Team> newList) {
        teamList.clear();
        if (newList != null) teamList.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TeamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.team_item, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TeamViewHolder holder, int position) {
        Team team = teamList.get(position);

        holder.teamName.setText(AppUtils.safe(team.getTeamName()));
        holder.teamRegion.setText(AppUtils.safe(team.getRegion()));

        int skill = team.getSkillAverage() != null ? team.getSkillAverage().intValue() : 0;
        holder.teamSkill.setText("실력: " + skill);
        holder.teamAge.setText("나이: " + AppUtils.nz(team.getAgeRange(), "-"));

        GlideHelper.loadTeamLogo(context, team.getLogoUrl(), holder.teamLogo);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(team);
        });
    }

    @Override
    public int getItemCount() {
        return teamList.size();
    }

    public static class TeamViewHolder extends RecyclerView.ViewHolder {
        ImageView teamLogo;
        TextView  teamName, teamRegion, teamSkill, teamAge;

        public TeamViewHolder(@NonNull View itemView) {
            super(itemView);
            teamLogo   = itemView.findViewById(R.id.teamLogo);
            teamName   = itemView.findViewById(R.id.teamName);
            teamRegion = itemView.findViewById(R.id.teamRegion);
            teamSkill  = itemView.findViewById(R.id.teamSkill);
            teamAge    = itemView.findViewById(R.id.teamAge);
        }
    }
}