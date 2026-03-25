package com.example.englishflow.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.DomainItem;
import com.example.englishflow.ui.adapters.DomainAdapter;

import java.util.List;

public class LearnDomainsFragment extends Fragment {

    private DomainAdapter adapter;
    private RecyclerView recyclerView;
    private boolean isAscending = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learn_domains, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.domainRecycler);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        List<DomainItem> domains = AppRepository.getInstance(requireContext()).getDomains();
        adapter = new DomainAdapter(domains, domainItem -> {
            Fragment parent = getParentFragment();
            if (parent instanceof LearnFlowNavigator) {
                ((LearnFlowNavigator) parent).openTopics(domainItem);
            }
        });
        recyclerView.setAdapter(adapter);

        // Search logic
        EditText searchInput = view.findViewById(R.id.domainSearchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Sort logic (Custom Premium Popup)
        ImageButton btnSort = view.findViewById(R.id.btnSort);
        btnSort.setOnClickListener(v -> showCustomSortPopup(btnSort));

        // View toggle logic
        ImageButton btnToggleView = view.findViewById(R.id.btnToggleView);
        btnToggleView.setOnClickListener(v -> {
            if (adapter.getViewType() == DomainAdapter.VIEW_TYPE_GRID) {
                adapter.setViewType(DomainAdapter.VIEW_TYPE_LIST);
                recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
                btnToggleView.setImageResource(R.drawable.ic_list); // Assuming ic_list exists, or swap icons
                btnToggleView.setImageResource(R.drawable.ic_grid); // Oops, I'll just change the icon to the *next* view type
                btnToggleView.setImageResource(android.R.drawable.ic_dialog_dialer); // Placeholder for grid icon
            } else {
                adapter.setViewType(DomainAdapter.VIEW_TYPE_GRID);
                recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
                btnToggleView.setImageResource(android.R.drawable.ic_menu_sort_by_size); // Placeholder for list icon
            }
            // Realistically, swap between ic_grid and ic_list
            updateToggleIcon(btnToggleView);
        });
        updateToggleIcon(btnToggleView);
    }

    private void updateToggleIcon(ImageButton btn) {
        if (adapter.getViewType() == DomainAdapter.VIEW_TYPE_GRID) {
            btn.setImageResource(R.drawable.ic_grid); // Grid icon means "I am in grid" OR "switch to grid"? 
            // Usually, icon shows current state or available action. Let's make it show the action.
            // If in grid, show list icon.
            btn.setImageResource(R.drawable.ic_list); 
        } else {
            btn.setImageResource(R.drawable.ic_grid);
        }
    }

    private void showCustomSortPopup(View anchor) {
        View popupView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_sort_popup, null);
        PopupWindow popup = new PopupWindow(popupView, 
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true);
        
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        // Setup Items
        setupSortOption(popupView.findViewById(R.id.sortNameAsc), "Tên (A-Z)", () -> {
            adapter.sort(true);
            popup.dismiss();
        });
        setupSortOption(popupView.findViewById(R.id.sortNameDesc), "Tên (Z-A)", () -> {
            adapter.sort(false);
            popup.dismiss();
        });
        setupSortOption(popupView.findViewById(R.id.sortProgressLow), "Tiến độ thấp nhất", () -> {
            adapter.sortByProgress(true);
            popup.dismiss();
        });
        setupSortOption(popupView.findViewById(R.id.sortProgressHigh), "Tiến độ cao nhất", () -> {
            adapter.sortByProgress(false);
            popup.dismiss();
        });

        popup.setElevation(20f);
        popup.showAsDropDown(anchor, 0, 10);
    }

    private void setupSortOption(View container, String label, Runnable action) {
        TextView txtLabel = container.findViewById(R.id.txtSortLabel);
        txtLabel.setText(label);
        container.setOnClickListener(v -> action.run());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || adapter == null) {
            return;
        }
        List<DomainItem> domains = AppRepository.getInstance(requireContext()).getDomains();
        adapter.submitDomains(domains);
    }
}

