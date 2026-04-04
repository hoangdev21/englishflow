package com.example.englishflow.admin.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.LoginActivity;
import com.example.englishflow.MainActivity;
import com.example.englishflow.R;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminSettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        TextView adminName = view.findViewById(R.id.adminProfileName);
        TextView adminEmail = view.findViewById(R.id.adminProfileEmail);
        MaterialButton btnLogout = view.findViewById(R.id.adminBtnLogout);
        MaterialButton btnBack = view.findViewById(R.id.adminBtnBackToApp);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            adminName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Administrator");
            adminEmail.setText(user.getEmail());
        }

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });

        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), MainActivity.class));
            requireActivity().finish();
        });
    }
}
