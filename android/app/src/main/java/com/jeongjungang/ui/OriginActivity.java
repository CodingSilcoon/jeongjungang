package com.jeongjungang.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.jeongjungang.R;
import com.jeongjungang.data.remote.GeoPlace;
import com.jeongjungang.databinding.ActivityOriginBinding;
import com.jeongjungang.databinding.ItemOriginBinding;
import com.jeongjungang.databinding.SheetOriginCriteriaBinding;
import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.ServiceArea;
import com.jeongjungang.ui.recommend.RecommendViewModel;
import com.jeongjungang.ui.search.AddressSearchViewModel;
import com.jeongjungang.ui.search.SearchState;

import java.util.ArrayList;

/** 출발지 입력 → 실제 장소 검색 → 추천 기준 선택 화면. */
public class OriginActivity extends AppCompatActivity {

    public static final String PICK_ORIGIN = "pickOrigin";

    private static final int ENTRY = 0;
    private static final int SEARCH = 1;
    private static final int CRITERIA = 2;

    private ActivityOriginBinding binding;
    private AddressSearchViewModel searchModel;
    private SheetOriginCriteriaBinding criteriaBinding;
    private BottomSheetDialog criteriaDialog;

    // 첫 행은 내 위치이며, null인 행은 아직 출발지를 선택하지 않은 상태다.
    private final ArrayList<GeoPlace> origins = new ArrayList<>();
    private int screen = ENTRY;
    private int editingIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityOriginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        searchModel = new ViewModelProvider(this).get(AddressSearchViewModel.class);

        configureInsets();
        restoreInput(savedInstanceState);
        configureCriteriaSheet();
        bindNavigation();
        bindSearch();
        renderParticipants();
        showScreen(screen);

        if (savedInstanceState != null) {
            binding.searchInput.setText(savedInstanceState.getString("query", ""));
            criteriaBinding.criteriaGroup.check(savedInstanceState.getInt(
                    "criterion", R.id.totalCriterion));

            int scrollY = savedInstanceState.getInt("participantScroll", 0);
            binding.participantScroll.post(() ->
                    binding.participantScroll.scrollTo(0, scrollY));
        }

        searchModel.getSearchState().observe(this, this::renderSearch);

        // 약속 만들기에서는 같은 검색 화면을 출발지 선택기로 재사용한다.
        if (getIntent().getBooleanExtra(PICK_ORIGIN, false) && savedInstanceState == null) {
            openSearch(0);
        }
    }

    /** 시스템 표시줄과 검색 키보드가 화면을 가리지 않게 한다. */
    private void configureInsets() {
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                .setAppearanceLightNavigationBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());

            view.setPadding(safe.left, safe.top, safe.right,
                    Math.max(safe.bottom, keyboard.bottom));
            return insets;
        });
    }

    /** 하단 팝업은 출발지 화면 위에 표시하고, 닫아도 입력을 보존한다. */
    private void configureCriteriaSheet() {
        criteriaBinding = SheetOriginCriteriaBinding.inflate(getLayoutInflater());
        criteriaDialog = new BottomSheetDialog(this);
        criteriaDialog.setContentView(criteriaBinding.getRoot());
        criteriaDialog.setCanceledOnTouchOutside(true);

        criteriaDialog.setOnShowListener(dialog -> {
            View sheet = criteriaDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(R.drawable.origin_sheet_background);
            }

            // 처음부터 선택지와 확인 버튼이 보이도록 펼친다.
            criteriaDialog.getBehavior().setSkipCollapsed(true);
            criteriaDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        criteriaDialog.setOnDismissListener(dialog -> {
            screen = ENTRY;
            binding.findButton.requestFocus();
        });
    }

    /** 추가·뒤로가기·다음 단계의 동작을 한곳에서 연결한다. */
    private void bindNavigation() {
        binding.backButton.setOnClickListener(view -> goBack());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });

        binding.addButton.setOnClickListener(view -> {
            if (origins.size() >= RecommendViewModel.MAX_PARTICIPANTS) {
                return;
            }

            origins.add(null);
            renderParticipants();

            // 새 친구와 추가 버튼이 보이도록 목록 안에서만 이동한다.
            binding.participantScroll.post(() ->
                    binding.participantScroll.fullScroll(View.FOCUS_DOWN));
        });

        binding.findButton.setOnClickListener(view -> {
            if (!origins.contains(null)) {
                showScreen(CRITERIA);
            }
        });

        // 좌표와 선택 기준을 결과 화면에 넘겨 실제 추천을 계산한다.
        criteriaBinding.confirmButton.setOnClickListener(view -> {
            boolean fair = criteriaBinding.criteriaGroup.getCheckedRadioButtonId()
                    == R.id.fairCriterion;
            criteriaDialog.dismiss();
            startActivity(RecommendationActivity.createIntent(this, origins, fair));
        });
    }

    /** 실제 검색 계층을 사용한다. 서버가 없으면 번들 역 검색으로 동작한다. */
    private void bindSearch() {
        binding.searchInput.setHint(searchModel.usesServer()
                ? R.string.origin_search_hint : R.string.origin_station_hint);
        binding.searchNotice.setText(R.string.origin_search_local);
        binding.searchNotice.setVisibility(searchModel.usesServer() ? View.GONE : View.VISIBLE);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                // 입력이 바뀐 뒤에만 검색한다.
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                binding.searchResults.removeAllViews();
                binding.searchStatus.setText(text.toString().trim().length() < 2
                        ? R.string.origin_search_prompt : R.string.origin_search_loading);
                searchModel.onQueryChanged(text.toString());
            }

            @Override
            public void afterTextChanged(Editable text) {
                // 추가 편집은 하지 않는다.
            }
        });

        binding.searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }

            searchModel.search(binding.searchInput.getText().toString());
            return true;
        });
    }

    /** 나 → 친구 1 → 친구 2 순서로 카드를 그린다. */
    private void renderParticipants() {
        binding.participantList.removeAllViews();

        for (int index = 0; index < origins.size(); index++) {
            final int position = index;
            GeoPlace place = origins.get(index);
            ItemOriginBinding row = ItemOriginBinding.inflate(
                    getLayoutInflater(), binding.participantList, false);

            row.name.setText(participantName(index));
            row.number.setText(index == 0
                    ? getString(R.string.origin_me_badge) : String.valueOf(index));
            row.number.setBackground(createBadge(index));

            String placeName = place == null
                    ? getString(R.string.origin_choose) : place.name;
            row.chooseButton.setText(placeName);
            row.chooseButton.setTextColor(ContextCompat.getColor(this,
                    place == null ? R.color.start_muted : R.color.start_ink));
            row.chooseButton.setContentDescription(getString(
                    R.string.origin_choose_named, participantName(index), placeName));
            row.chooseButton.setOnClickListener(view -> openSearch(position));

            boolean canDelete = index > 0 && origins.size() > 2;
            row.deleteButton.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            row.deleteButton.setContentDescription(getString(
                    R.string.origin_delete_named, participantName(index)));
            row.deleteButton.setOnClickListener(view -> {
                origins.remove(position);
                renderParticipants();
            });

            binding.participantList.addView(row.getRoot());
        }

        int remaining = 0;
        for (GeoPlace place : origins) {
            if (place == null) {
                remaining++;
            }
        }

        binding.entryHint.setText(remaining == 0
                ? getString(R.string.origin_ready, origins.size())
                : getString(R.string.origin_remaining, remaining));
        binding.findButton.setEnabled(remaining == 0);
        binding.findButton.setAlpha(remaining == 0 ? 1f : 0.48f);

        boolean canAdd = origins.size() < RecommendViewModel.MAX_PARTICIPANTS;
        binding.addButton.setEnabled(canAdd);
        binding.addButton.setText(canAdd ? R.string.origin_add : R.string.origin_limit);
    }

    /** 행 번호를 레몬·연두·살구 배지로 구분한다. */
    private GradientDrawable createBadge(int index) {
        int[] colors = {0xFFF9DA49, 0xFFDEEDAA, 0xFFF2B7A6};
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(colors[index % colors.length]);
        badge.setCornerRadius(dp(7));
        badge.setStroke(dp(1), ContextCompat.getColor(this, R.color.start_ink));
        return badge;
    }

    private String participantName(int index) {
        return index == 0 ? getString(R.string.origin_me)
                : getString(R.string.origin_friend, index);
    }

    /** 검색 대상을 기억하고 이전 검색 결과를 비운다. */
    private void openSearch(int index) {
        editingIndex = index;
        binding.searchInput.setText("");
        searchModel.clear();
        showScreen(SEARCH);

        binding.searchInput.requestFocus();
        binding.searchInput.post(() ->
                WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                        .show(WindowInsetsCompat.Type.ime()));
    }

    /** 늦게 도착한 다른 검색어의 결과는 화면에 올리지 않는다. */
    private void renderSearch(SearchState state) {
        String query = binding.searchInput.getText().toString()
                .trim().replaceAll("\\s+", " ");
        if (state == null || !state.query.equals(query)) {
            return;
        }

        binding.searchResults.removeAllViews();

        switch (state.status) {
            case IDLE:
                binding.searchStatus.setText(R.string.origin_search_prompt);
                break;
            case LOADING:
                binding.searchStatus.setText(R.string.origin_search_loading);
                break;
            case EMPTY:
                binding.searchStatus.setText(R.string.origin_search_empty);
                break;
            case ERROR:
                binding.searchStatus.setText(state.errorMessage == null
                        ? getString(R.string.origin_search_error) : state.errorMessage);
                break;
            case SUCCESS:
                binding.searchStatus.setText("");
                for (GeoPlace place : state.items) {
                    addSearchResult(place, state.isOutOfArea(place));
                }
                break;
        }
    }

    /** 검색 결과를 고르면 이름과 좌표를 함께 저장한다. */
    private void addSearchResult(GeoPlace place, boolean outside) {
        AppCompatButton result = new AppCompatButton(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        result.setLayoutParams(params);
        result.setMinHeight(dp(72));
        result.setPadding(dp(16), dp(14), dp(16), dp(14));
        result.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        result.setTextSize(15);
        result.setAllCaps(false);
        result.setTextColor(ContextCompat.getColor(this, R.color.start_ink));
        result.setBackgroundResource(R.drawable.start_action_secondary);
        result.setSupportBackgroundTintList(null);

        String detail = place.address == null ? "" : place.address;
        if (outside) {
            detail = getString(R.string.origin_outside) + " · " + detail;
        }
        result.setText(place.name + (detail.isEmpty() ? "" : "\n" + detail));
        result.setAlpha(outside ? 0.6f : 1f);

        result.setOnClickListener(view -> {
            if (outside) {
                Snackbar.make(binding.getRoot(), ServiceArea.MESSAGE,
                        Snackbar.LENGTH_LONG).show();
                return;
            }

            if (getIntent().getBooleanExtra(PICK_ORIGIN, false)) {
                android.content.Intent originResult = new android.content.Intent()
                        .putExtra("label", place.name)
                        .putExtra("lat", place.location.lat)
                        .putExtra("lng", place.location.lng);
                setResult(RESULT_OK, originResult);
                finish();
                return;
            }

            origins.set(editingIndex, place);
            searchModel.clear();
            renderParticipants();
            showScreen(ENTRY);
        });

        binding.searchResults.addView(result);
    }

    /** 검색은 화면 전환, 추천 기준은 입력 화면 위의 하단 팝업으로 연다. */
    private void showScreen(int next) {
        screen = next;
        binding.entryPanel.setVisibility(next == SEARCH ? View.GONE : View.VISIBLE);
        binding.searchPanel.setVisibility(next == SEARCH ? View.VISIBLE : View.GONE);

        binding.searchFor.setText(getString(
                R.string.origin_search_for, participantName(editingIndex)));

        if (next != SEARCH) {
            binding.searchInput.clearFocus();
            WindowCompat.getInsetsController(getWindow(), binding.getRoot())
                    .hide(WindowInsetsCompat.Type.ime());
        }

        if (next == CRITERIA && !criteriaDialog.isShowing()) {
            criteriaDialog.show();
        }
    }

    private void goBack() {
        if (getIntent().getBooleanExtra(PICK_ORIGIN, false)) {
            finish();
            return;
        }

        if (criteriaDialog.isShowing()) {
            criteriaDialog.dismiss();
            return;
        }

        if (screen == ENTRY) {
            finish();
        } else {
            searchModel.clear();
            showScreen(ENTRY);
        }
    }

    /** 회전과 프로세스 재생성 뒤에도 선택한 출발지와 기준을 복구한다. */
    private void restoreInput(Bundle state) {
        int count = state == null ? 2 : state.getInt("count", 2);

        for (int i = 0; i < count; i++) {
            Bundle place = state == null ? null : state.getBundle("place_" + i);
            origins.add(place == null ? null : new GeoPlace(
                    GeoPlace.Type.valueOf(place.getString("type")),
                    place.getString("name"),
                    place.getString("address"),
                    new LatLng(place.getDouble("lat"), place.getDouble("lng"))));
        }

        if (state != null) {
            screen = state.getInt("screen", ENTRY);
            editingIndex = Math.min(state.getInt("editingIndex", 0), origins.size() - 1);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("count", origins.size());
        state.putInt("screen", screen);
        state.putInt("editingIndex", editingIndex);
        state.putInt("criterion", criteriaBinding.criteriaGroup.getCheckedRadioButtonId());
        state.putInt("participantScroll", binding.participantScroll.getScrollY());
        state.putString("query", binding.searchInput.getText().toString());

        for (int i = 0; i < origins.size(); i++) {
            GeoPlace place = origins.get(i);
            if (place == null) {
                continue;
            }

            Bundle saved = new Bundle();
            saved.putString("type", place.type.name());
            saved.putString("name", place.name);
            saved.putString("address", place.address);
            saved.putDouble("lat", place.location.lat);
            saved.putDouble("lng", place.location.lng);
            state.putBundle("place_" + i, saved);
        }
    }

    @Override
    protected void onDestroy() {
        // 회전이나 화면 종료 시 이전 Activity의 팝업 창을 정리한다.
        criteriaDialog.setOnDismissListener(null);
        criteriaDialog.dismiss();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
