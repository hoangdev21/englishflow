package com.example.englishflow.data;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class FirebaseUserStore {

    public interface ProfileCallback {
        void onResult(String displayName);
    }

    private static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore firestore;

    public FirebaseUserStore() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void createUserProfile(@NonNull String uid, @NonNull String email, @NonNull String displayName) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("email", email);
        data.put("displayName", displayName);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("level", "A1");
        data.put("learnedWords", 0);

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(data);
    }

    public void updateDisplayName(@NonNull String uid, @NonNull String displayName) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", displayName);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .set(updates, SetOptions.merge());
    }

    public void fetchDisplayName(@NonNull String uid, @NonNull ProfileCallback callback) {
        firestore.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> callback.onResult(resolveDisplayName(snapshot)))
                .addOnFailureListener(e -> callback.onResult(null));
    }

    private String resolveDisplayName(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }

        String displayName = snapshot.getString("displayName");
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName.trim();
        }
        return null;
    }
}

