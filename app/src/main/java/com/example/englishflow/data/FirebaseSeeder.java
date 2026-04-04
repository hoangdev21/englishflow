package com.example.englishflow.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to seed initial data or promote specific users to administrative roles.
 */
public class FirebaseSeeder {
    private static final String TAG = "FirebaseSeeder";
    private static final String COLLECTION_USERS = "users";
    
    // The designated admin email
    private static final String ADMIN_EMAIL = "hoangdev21@gmail.com";
    private static final String ADMIN_PASSWORD = "hoangdev21";

    /**
     * Checks if the email is the designated admin email.
     */
    public static boolean isAdminEmail(String email) {
        if (email == null) return false;
        return ADMIN_EMAIL.equalsIgnoreCase(email.trim());
    }

    /**
     * Attempts to create the default admin account if it doesn't exist.
     * This is useful for initial setup.
     */
    public static void createDefaultAdminAccount() {
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        auth.createUserWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD)
                .addOnSuccessListener(result -> {
                    if (result.getUser() != null) {
                        Log.i(TAG, "Default admin account created successfully.");
                        promoteToAdmin(result.getUser().getUid());
                    }
                })
                .addOnFailureListener(e -> {
                    // It likely already exists, which is fine
                    Log.d(TAG, "Default admin creation skipped or failed: " + e.getMessage());
                });
    }
    /**
     * Checks if the given user email should be an admin and updates their role in Firestore if so.
     * 
     * @param uid The UID of the authenticated user
     * @param email The email address of the authenticated user
     */
    public static void seedAdminIfNeeded(@NonNull String uid, String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (isAdminEmail(normalizedEmail)) {
            Log.i(TAG, "Designated admin detected: " + normalizedEmail + ". Ensuring admin role in Firestore...");
            promoteToAdmin(uid);
        }
    }

    /**
     * Directly promotes a user to the 'admin' role in Firestore.
     * 
     * @param uid The UID of the user to promote
     */
    public static void promoteToAdmin(@NonNull String uid) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        
        Map<String, Object> adminPatch = new HashMap<>();
        adminPatch.put("role", "admin");
        adminPatch.put("updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(adminPatch, SetOptions.merge())
                .addOnSuccessListener(unused -> Log.d(TAG, "User " + uid + " successfully promoted to admin."))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to promote user " + uid + " to admin: " + e.getMessage()));
    }
}
