package com.jjw.soccerclub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

public class EventPickAdapter extends RecyclerView.Adapter<EventPickAdapter.VH> {

    public interface OnPick { void onPick(DocumentSnapshot d); }

    private final List<DocumentSnapshot> list;
    private final OnPick onPick;
    private final String myTeamId;
    private final String myTeamName;

    public EventPickAdapter(List<DocumentSnapshot> list, String myTeamId,
                            String myTeamName, OnPick onPick) {
        this.list = list;
        this.myTeamId   = AppUtils.safe(myTeamId);
        this.myTeamName = AppUtils.safe(myTeamName);
        this.onPick = onPick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DocumentSnapshot d = list.get(position);

        String homeTeamId   = AppUtils.safe(d.getString("homeTeamId"));
        String homeTeamName = AppUtils.safe(d.getString("homeTeamName"));
        String awayTeamName = AppUtils.safe(d.getString("awayTeamName"));
        String date         = AppUtils.safe(d.getString("date"));
        String time         = AppUtils.safe(d.getString("time"));
        String stadiumName  = AppUtils.firstNonEmpty(d.getString("stadiumName"), d.getString("stadium"));
        String address      = AppUtils.safe(d.getString("address"));

        boolean isHome = AppUtils.safeEquals(homeTeamId, myTeamId);
        String myName  = AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName;
        String oppName = isHome ? awayTeamName : homeTeamName;

        h.textTeams.setText(myName + " vs " + AppUtils.nz(oppName, "상대팀"));
        h.textDateTime.setText(DateUtils.appendWeekday(date) + "  |  " + AppUtils.nz(time, "-"));
        h.textStadiumName.setText(AppUtils.nz(stadiumName, "-"));
        h.textStadiumAddress.setText(AppUtils.safe(address));

        long matchTs = AppUtils.safeLong(d.getLong("matchTs"), 0L);
        if (matchTs <= 0) matchTs = DateUtils.computeStartMillis(date, time);
        h.chipStatus.setText(DateUtils.buildDDayText(matchTs));

        h.card.setOnClickListener(v -> { if (onPick != null) onPick.onPick(d); });
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        View card;
        TextView textTeams, chipStatus, textDateTime, textStadiumName, textStadiumAddress;
        ImageView iconCalendar, iconPin;

        public VH(@NonNull View v) {
            super(v);
            card                = v.findViewById(R.id.card);
            textTeams           = v.findViewById(R.id.textTeams);
            chipStatus          = v.findViewById(R.id.chipStatus);
            textDateTime        = v.findViewById(R.id.textDateTime);
            textStadiumName     = v.findViewById(R.id.textStadiumName);
            textStadiumAddress  = v.findViewById(R.id.textStadiumAddress);
            iconCalendar        = v.findViewById(R.id.iconCalendar);
            iconPin             = v.findViewById(R.id.iconPin);
        }
    }
}