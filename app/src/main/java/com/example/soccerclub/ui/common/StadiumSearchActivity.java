package com.example.soccerclub.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class StadiumSearchActivity extends AppCompatActivity implements OnMapReadyCallback {

    private EditText editSearch;
    private RecyclerView recyclerResults;
    private NaverMap naverMap;
    private Marker marker;

    private final List<SearchResult> resultList = new ArrayList<>();
    private ResultAdapter resultAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stadium_search);

        editSearch      = findViewById(R.id.editSearch);
        recyclerResults = findViewById(R.id.recyclerResults);

        recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        resultAdapter = new ResultAdapter(resultList, this::onResultSelected);
        recyclerResults.setAdapter(resultAdapter);

        MapFragment mapFragment = (MapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.mapFragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchPlaces(s.toString().trim());
            }
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int af) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        marker = new Marker();
        naverMap.moveCamera(CameraUpdate.toCameraPosition(
                new CameraPosition(new LatLng(37.5665, 126.9780), 13)));
    }

    private void searchPlaces(String query) {
        if (query.isEmpty()) {
            resultList.clear();
            resultAdapter.notifyDataSetChanged();
            return;
        }
        // TODO: 네이버 지도 장소 검색 API 연동
        // 현재는 더미 결과 표시
        resultList.clear();
        resultList.add(new SearchResult(query + " 구장", "서울 강남구 " + query, 37.5000, 127.0000));
        resultAdapter.notifyDataSetChanged();
    }

    private void onResultSelected(SearchResult result) {
        if (naverMap != null && marker != null) {
            LatLng latLng = new LatLng(result.lat, result.lng);
            marker.setPosition(latLng);
            marker.setMap(naverMap);
            naverMap.moveCamera(CameraUpdate.toCameraPosition(
                    new CameraPosition(latLng, 15)));
        }

        Intent data = new Intent();
        data.putExtra("stadium_name", result.name);
        data.putExtra("address",      result.address);
        setResult(RESULT_OK, data);
        finish();
    }

    static class SearchResult {
        String name, address;
        double lat, lng;
        SearchResult(String name, String address, double lat, double lng) {
            this.name = name; this.address = address;
            this.lat = lat;   this.lng = lng;
        }
    }

    static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        interface OnSelect { void onSelect(SearchResult r); }
        private final List<SearchResult> items;
        private final OnSelect listener;
        ResultAdapter(List<SearchResult> items, OnSelect listener) {
            this.items = items; this.listener = listener;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            SearchResult r = items.get(pos);
            h.text1.setText(r.name);
            h.text2.setText(r.address);
            h.itemView.setOnClickListener(v -> listener.onSelect(r));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(@NonNull View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
                text2 = v.findViewById(android.R.id.text2);
            }
        }
    }
}