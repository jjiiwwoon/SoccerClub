package com.jjw.soccerclub.ui.common;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.google.android.material.button.MaterialButton;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapView;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StadiumSearchActivity extends BaseActivity implements OnMapReadyCallback {

    private static final String TAG = "StadiumSearchActivity";

    private EditText        editSearch;
    private MaterialButton  btnSearch, btnSelectStadium;
    private RecyclerView    recyclerResults;
    private LinearLayout    layoutResults;
    private MapView         mapView;
    private NaverMap        naverMap;
    private Marker          marker;

    private String selectedAddress   = "";
    private String selectedPlaceName = "";
    private double selectedLat       = 0;
    private double selectedLng       = 0;

    private final List<SearchResult> resultList = new ArrayList<>();
    private ResultAdapter resultAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── onCreate ─────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stadium_search);

        editSearch       = findViewById(R.id.editSearch);
        btnSearch        = findViewById(R.id.btnSearch);
        btnSelectStadium = findViewById(R.id.btnSelectStadium);
        recyclerResults  = findViewById(R.id.recyclerResults);
        layoutResults    = findViewById(R.id.layoutResults);
        mapView          = findViewById(R.id.mapView);

        recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        resultAdapter = new ResultAdapter(resultList, this::onResultSelected);
        recyclerResults.setAdapter(resultAdapter);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 검색 버튼 클릭
        btnSearch.setOnClickListener(v -> {
            String q = editSearch.getText().toString().trim();
            if (!q.isEmpty()) { hideKeyboard(); searchPlaces(q); }
        });

        // 키보드 검색 버튼
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = editSearch.getText().toString().trim();
                if (!q.isEmpty()) { hideKeyboard(); searchPlaces(q); }
                return true;
            }
            return false;
        });

        // 이 장소 선택
        btnSelectStadium.setOnClickListener(v -> {
            if (selectedLat == 0 && selectedLng == 0) {
                CustomToast.warning(this, "지도에서 장소를 선택하거나 검색 결과를 탭하세요.");
                return;
            }
            Intent result = new Intent();
            result.putExtra("stadium_name", selectedPlaceName);
            result.putExtra("address",      selectedAddress);
            result.putExtra("lat",          selectedLat);
            result.putExtra("lng",          selectedLng);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    // ── 지도 준비 ─────────────────────────────────────────────────────────────────

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;
        marker = new Marker();

        // 초기 위치: 서울 시청
        naverMap.moveCamera(CameraUpdate.toCameraPosition(
                new CameraPosition(new LatLng(37.5665, 126.9780), 12)));

        // 지도 클릭 → 마커 이동 + 역지오코딩
        naverMap.setOnMapClickListener((point, latLng) -> {
            placeMarker(latLng.latitude, latLng.longitude, "");
            reverseGeocode(latLng.latitude, latLng.longitude);
        });
    }

    // ── 장소 검색 (Android 내장 Geocoder — 추가 API 키 불필요) ───────────────────

    private void searchPlaces(String query) {
        if (layoutResults != null) layoutResults.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.KOREA);
                List<Address> addresses = geocoder.getFromLocationName(query, 10);

                List<SearchResult> results = new ArrayList<>();
                if (addresses != null) {
                    for (Address addr : addresses) {
                        String fullAddr = addr.getAddressLine(0);
                        if (fullAddr == null) fullAddr = query;
                        String name = addr.getFeatureName() != null
                                ? addr.getFeatureName() : fullAddr;
                        results.add(new SearchResult(
                                name, fullAddr,
                                addr.getLatitude(), addr.getLongitude()));
                    }
                }

                runOnUiThread(() -> {
                    resultList.clear();
                    resultList.addAll(results);
                    resultAdapter.notifyDataSetChanged();
                    if (results.isEmpty())
                        CustomToast.info(this, "검색 결과가 없어요. 다른 키워드로 시도해보세요.");
                });

            } catch (IOException e) {
                Log.e(TAG, "Geocoder error: " + e.getMessage());
                runOnUiThread(() -> CustomToast.error(this, "검색 중 오류가 발생했어요."));
            }
        });
    }

    // ── 역지오코딩 (좌표 → 주소) ─────────────────────────────────────────────────

    private void reverseGeocode(double lat, double lng) {
        executor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.KOREA);
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String addr = addresses.get(0).getAddressLine(0);
                    runOnUiThread(() -> {
                        selectedAddress   = addr != null ? addr : "";
                        selectedPlaceName = selectedAddress;
                        if (!selectedAddress.isEmpty())
                            CustomToast.info(this, "선택된 주소: " + selectedAddress);
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Reverse geocode error: " + e.getMessage());
            }
        });
    }

    // ── 결과 선택 ─────────────────────────────────────────────────────────────────

    private void onResultSelected(SearchResult result) {
        if (layoutResults != null) layoutResults.setVisibility(View.GONE);
        placeMarker(result.lat, result.lng, result.name);
        selectedAddress   = result.address;
        selectedPlaceName = result.name;
        selectedLat       = result.lat;
        selectedLng       = result.lng;
    }

    private void placeMarker(double lat, double lng, String title) {
        if (naverMap == null || marker == null) return;
        LatLng position = new LatLng(lat, lng);
        selectedLat = lat;
        selectedLng = lng;
        marker.setPosition(position);
        marker.setMap(naverMap);
        if (!title.isEmpty()) marker.setCaptionText(title);
        naverMap.moveCamera(
                CameraUpdate.toCameraPosition(new CameraPosition(position, 16))
                        .animate(CameraAnimation.Easing));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && editSearch != null)
            imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
    }

    // ── MapView 생명주기 ──────────────────────────────────────────────────────────

    @Override protected void onStart()   { super.onStart();   mapView.onStart(); }
    @Override protected void onResume()  { super.onResume();  mapView.onResume(); }
    @Override protected void onPause()   { super.onPause();   mapView.onPause(); }
    @Override protected void onStop()    { super.onStop();    mapView.onStop(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        executor.shutdown();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    // ── 모델 ─────────────────────────────────────────────────────────────────────

    static class SearchResult {
        String name, address;
        double lat, lng;
        SearchResult(String n, String a, double lat, double lng) {
            name = n; address = a; this.lat = lat; this.lng = lng;
        }
    }

    // ── 어댑터 ────────────────────────────────────────────────────────────────────

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

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
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