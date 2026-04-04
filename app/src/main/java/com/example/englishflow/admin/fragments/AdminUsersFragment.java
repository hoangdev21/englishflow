package com.example.englishflow.admin.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.englishflow.R;
import com.example.englishflow.admin.AdminUserAdapter;
import com.example.englishflow.admin.AdminUserDetailActivity;
import com.example.englishflow.data.CloudUserProfile;
import com.example.englishflow.data.FirebaseUserStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AdminUsersFragment extends Fragment implements AdminUserAdapter.Listener {

    private static final long RECENT_WINDOW_MS = 15L * 60L * 1000L;
    private static final String ROLE_FILTER_ALL = "Tất cả";
    private static final String ROLE_FILTER_ADMIN = "Admin";
    private static final String ROLE_FILTER_USER = "User";
    private static final String[] ROLE_FILTER_OPTIONS = new String[] {
        ROLE_FILTER_ALL,
        ROLE_FILTER_ADMIN,
        ROLE_FILTER_USER
    };

    private static final String STATUS_FILTER_ALL = "Tất cả";
    private static final String STATUS_FILTER_ACTIVE = "Hoạt động";
    private static final String STATUS_FILTER_LOCKED = "Đã khóa";
    private static final String STATUS_FILTER_DELETED = "Đã xóa";
    private static final String[] STATUS_FILTER_OPTIONS = new String[] {
        STATUS_FILTER_ALL,
        STATUS_FILTER_ACTIVE,
        STATUS_FILTER_LOCKED,
        STATUS_FILTER_DELETED
    };

    private static final String ACTIVITY_FILTER_ALL = "Tất cả";
    private static final String ACTIVITY_FILTER_ONLINE = "Online";
    private static final String ACTIVITY_FILTER_RECENT = "Gần đây";
    private static final String ACTIVITY_FILTER_OFFLINE = "Offline";
    private static final String[] ACTIVITY_FILTER_OPTIONS = new String[] {
        ACTIVITY_FILTER_ALL,
        ACTIVITY_FILTER_ONLINE,
        ACTIVITY_FILTER_RECENT,
        ACTIVITY_FILTER_OFFLINE
    };

    private final FirebaseUserStore userStore = new FirebaseUserStore();
    private final List<CloudUserProfile> allUsers = new ArrayList<>();

    private AdminUserAdapter adapter;
    private TextInputEditText searchInput;
    private AutoCompleteTextView roleFilterInput;
    private AutoCompleteTextView statusFilterInput;
    private AutoCompleteTextView activityFilterInput;
    private TextView usersStats;
    private View emptyState;
    private FirebaseUserStore.UsersSubscription usersSubscription;
    private String selectedRoleFilter = ROLE_FILTER_ALL;
    private String selectedStatusFilter = STATUS_FILTER_ALL;
    private String selectedActivityFilter = ACTIVITY_FILTER_ALL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchInput = view.findViewById(R.id.adminSearchInput);
        roleFilterInput = view.findViewById(R.id.adminRoleFilterInput);
        statusFilterInput = view.findViewById(R.id.adminStatusFilterInput);
        activityFilterInput = view.findViewById(R.id.adminActivityFilterInput);
        usersStats = view.findViewById(R.id.adminUsersStats);
        emptyState = view.findViewById(R.id.adminEmptyState);

        RecyclerView recyclerView = view.findViewById(R.id.adminUsersRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AdminUserAdapter(this);
        recyclerView.setAdapter(adapter);

        setupSearch();
        setupFilterDropdowns();
        fetchUsersOnce();
    }

    @Override
    public void onStart() {
        super.onStart();
        observeUsersRealtime();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (usersSubscription != null) {
            usersSubscription.remove();
            usersSubscription = null;
        }
    }

    private void setupSearch() {
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFiltersAndSort();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupFilterDropdowns() {
        setupFilterDropdown(roleFilterInput, ROLE_FILTER_OPTIONS, selectedRoleFilter,
                selected -> selectedRoleFilter = selected);
        setupFilterDropdown(statusFilterInput, STATUS_FILTER_OPTIONS, selectedStatusFilter,
                selected -> selectedStatusFilter = selected);
        setupFilterDropdown(activityFilterInput, ACTIVITY_FILTER_OPTIONS, selectedActivityFilter,
                selected -> selectedActivityFilter = selected);
    }

    private void setupFilterDropdown(
            @Nullable AutoCompleteTextView input,
            @NonNull String[] options,
            @NonNull String selected,
            @NonNull FilterSelectionListener listener
    ) {
        if (input == null) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_admin_dropdown_option,
                options
        );
        adapter.setDropDownViewResource(R.layout.item_admin_dropdown_option);
        input.setAdapter(adapter);
        input.setText(selected, false);
        input.setOnClickListener(v -> input.showDropDown());
        input.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < options.length) {
                listener.onSelected(options[position]);
                applyFiltersAndSort();
            }
        });
    }

    private interface FilterSelectionListener {
        void onSelected(@NonNull String selected);
    }

    private void fetchUsersOnce() {
        userStore.fetchUsers(users -> {
            if (!isAdded()) {
                return;
            }
            allUsers.clear();
            allUsers.addAll(users);
            applyFiltersAndSort();
        });
    }

    private void observeUsersRealtime() {
        if (usersSubscription != null) {
            usersSubscription.remove();
        }
        usersSubscription = userStore.observeUsers(users -> {
            if (!isAdded()) {
                return;
            }
            allUsers.clear();
            allUsers.addAll(users);
            applyFiltersAndSort();
        });
    }

    private void applyFiltersAndSort() {
        String query = searchInput != null && searchInput.getText() != null
                ? searchInput.getText().toString().trim().toLowerCase(Locale.US)
                : "";
        long now = System.currentTimeMillis();

        List<CloudUserProfile> filtered = new ArrayList<>();
        for (CloudUserProfile user : allUsers) {
            if (!matchesQuery(user, query)) {
                continue;
            }
            if (!matchesRoleFilter(user)) {
                continue;
            }
            if (!matchesStatusFilter(user)) {
                continue;
            }
            if (!matchesActivityFilter(user, now)) {
                continue;
            }
            filtered.add(user);
        }

        sortUsers(filtered);
        adapter.updateData(filtered);
        if (emptyState != null) {
            emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
        updateStats(filtered, now);
    }

    private boolean matchesQuery(@NonNull CloudUserProfile user, @NonNull String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String haystack = (safe(user.displayName)
                + " " + safe(user.email)
                + " " + safe(user.role)
                + " " + safe(user.status)).toLowerCase(Locale.US);
        return haystack.contains(normalizedQuery);
    }

    private boolean matchesRoleFilter(@NonNull CloudUserProfile user) {
        if (ROLE_FILTER_ADMIN.equals(selectedRoleFilter)) {
            return user.isAdmin();
        }
        if (ROLE_FILTER_USER.equals(selectedRoleFilter)) {
            return !user.isAdmin();
        }
        return true;
    }

    private boolean matchesStatusFilter(@NonNull CloudUserProfile user) {
        if (STATUS_FILTER_ACTIVE.equals(selectedStatusFilter)) {
            return FirebaseUserStore.STATUS_ACTIVE.equalsIgnoreCase(user.status);
        }
        if (STATUS_FILTER_LOCKED.equals(selectedStatusFilter)) {
            return FirebaseUserStore.STATUS_LOCKED.equalsIgnoreCase(user.status);
        }
        if (STATUS_FILTER_DELETED.equals(selectedStatusFilter)) {
            return FirebaseUserStore.STATUS_DELETED.equalsIgnoreCase(user.status);
        }
        return true;
    }

    private boolean matchesActivityFilter(@NonNull CloudUserProfile user, long now) {
        if (ACTIVITY_FILTER_ONLINE.equals(selectedActivityFilter)) {
            return user.isActiveNow(now);
        }

        long lastSeen = user.getLastSeenAt();
        boolean isRecent = lastSeen > 0L && (now - lastSeen) <= RECENT_WINDOW_MS;

        if (ACTIVITY_FILTER_RECENT.equals(selectedActivityFilter)) {
            return !user.isActiveNow(now) && isRecent;
        }
        if (ACTIVITY_FILTER_OFFLINE.equals(selectedActivityFilter)) {
            return !user.isActiveNow(now) && !isRecent;
        }
        return true;
    }

    private void sortUsers(@NonNull List<CloudUserProfile> users) {
        Collections.sort(users, (left, right) -> Long.compare(right.getLastSeenAt(), left.getLastSeenAt()));
    }

    private void updateStats(@NonNull List<CloudUserProfile> shownUsers, long now) {
        if (usersStats == null) {
            return;
        }
        int onlineCount = 0;
        for (CloudUserProfile profile : shownUsers) {
            if (profile.isActiveNow(now)) {
                onlineCount++;
            }
        }
        usersStats.setText(shownUsers.size() + "/" + allUsers.size() + " người dùng | " + onlineCount + " đang hoạt động");
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onViewDetail(CloudUserProfile profile) {
        Intent intent = new Intent(requireContext(), AdminUserDetailActivity.class);
        intent.putExtra(AdminUserDetailActivity.EXTRA_USER_UID, profile.uid);
        startActivity(intent);
    }

    @Override
    public void onEditDisplayName(CloudUserProfile profile) {
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        TextInputEditText editText = new TextInputEditText(requireContext());
        editText.setHint("Tên hiển thị mới");
        editText.setText(profile.displayName);
        editText.setSelection(editText.getText() != null ? editText.getText().length() : 0);

        int horizontal = dpToPx(20);
        int vertical = dpToPx(8);
        inputLayout.setPadding(horizontal, vertical, horizontal, 0);
        inputLayout.addView(editText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sửa tên hiển thị")
                .setView(inputLayout)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String nextName = editText.getText() == null ? "" : editText.getText().toString().trim();
                    userStore.updateDisplayNameByAdmin(profile.uid, nextName, (success, msg) -> {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(
                                requireContext(),
                                success ? "Đã cập nhật tên" : nonEmpty(msg, "Không thể cập nhật tên"),
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                })
                .show();
    }

    @Override
    public void onToggleRole(CloudUserProfile profile) {
        String nextRole = profile.isAdmin() ? FirebaseUserStore.ROLE_USER : FirebaseUserStore.ROLE_ADMIN;
        String message = profile.isAdmin()
                ? "Hạ quyền tài khoản này xuống USER?"
                : "Nâng quyền tài khoản này lên ADMIN?";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xác nhận đổi quyền")
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        userStore.updateRole(profile.uid, nextRole, (success, msg) -> {
                            if (!isAdded()) {
                                return;
                            }
                            Toast.makeText(
                                    requireContext(),
                                    success ? "Đã cập nhật quyền" : nonEmpty(msg, "Lỗi cập nhật quyền"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        })
                )
                .show();
    }

    @Override
    public void onResetPassword(CloudUserProfile profile) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đặt lại mật khẩu")
                .setMessage("Gửi email đặt lại mật khẩu tới " + profile.email + "?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi email", (dialog, which) ->
                        userStore.sendPasswordResetForUser(profile.email, (success, msg) -> {
                            if (!isAdded()) {
                                return;
                            }
                            Toast.makeText(
                                    requireContext(),
                                    success ? "Đã gửi email đặt lại mật khẩu" : nonEmpty(msg, "Không thể gửi email"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        })
                )
                .show();
    }

    @Override
    public void onDeleteUser(CloudUserProfile profile) {
        if (profile.isDeleted()) {
            Toast.makeText(requireContext(), "Tài khoản này đã được đánh dấu xóa", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa người dùng")
                .setMessage("Tài khoản sẽ bị đánh dấu xóa, ngừng truy cập app. Bạn chắc chắn?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) ->
                        userStore.softDeleteUser(profile.uid, (success, msg) -> {
                            if (!isAdded()) {
                                return;
                            }
                            Toast.makeText(
                                    requireContext(),
                                    success ? "Đã đánh dấu xóa người dùng" : nonEmpty(msg, "Không thể xóa người dùng"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        })
                )
                .show();
    }

    @Override
    public void onToggleStatus(CloudUserProfile profile) {
        String nextStatus = profile.isLocked() ? FirebaseUserStore.STATUS_ACTIVE : FirebaseUserStore.STATUS_LOCKED;
        String title = profile.isLocked() ? "Kích hoạt tài khoản" : "Khóa tài khoản";
        String message = profile.isLocked()
                ? "Mở lại quyền truy cập cho người dùng này?"
                : "Người dùng sẽ không thể đăng nhập cho đến khi mở khóa. Tiếp tục?";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (dialog, which) ->
                        userStore.updateStatus(profile.uid, nextStatus, (success, msg) -> {
                            if (!isAdded()) {
                                return;
                            }
                            Toast.makeText(
                                    requireContext(),
                                    success ? "Đã cập nhật trạng thái" : nonEmpty(msg, "Lỗi cập nhật trạng thái"),
                                    Toast.LENGTH_SHORT
                            ).show();
                        })
                )
                .show();
    }

    @NonNull
    private String nonEmpty(@Nullable String value, @NonNull String fallback) {
        String safeValue = value == null ? "" : value.trim();
        return safeValue.isEmpty() ? fallback : safeValue;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
