package com.example.englishflow.ui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.ScanResult;
import com.example.englishflow.data.WordEntry;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class ScanFragment extends Fragment {

    private AppRepository repository;
    private ScanResult currentResult;
    private TextToSpeech textToSpeech;

    private ImageView previewImage;
    private TextView wordText;
    private TextView ipaMeaningText;
    private TextView exampleText;
    private TextView categoryText;
    private TextView funFactText;
    private TextView relatedText;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());

        previewImage = view.findViewById(R.id.scanPreview);
        wordText = view.findViewById(R.id.scanWord);
        ipaMeaningText = view.findViewById(R.id.scanIpaMeaning);
        exampleText = view.findViewById(R.id.scanExample);
        categoryText = view.findViewById(R.id.scanCategory);
        funFactText = view.findViewById(R.id.scanFunFact);
        relatedText = view.findViewById(R.id.scanRelated);

        MaterialButton takePhotoButton = view.findViewById(R.id.btnTakePhoto);
        MaterialButton pickGalleryButton = view.findViewById(R.id.btnPickGallery);
        MaterialButton analyzeButton = view.findViewById(R.id.btnAnalyzeAi);
        MaterialButton pronounceButton = view.findViewById(R.id.btnPronounceScan);
        MaterialButton saveButton = view.findViewById(R.id.btnSaveScan);

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImagePicked);

        currentResult = repository.mockScanResult();
        bindResult(currentResult);

        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });

        takePhotoButton.setOnClickListener(v -> Toast.makeText(requireContext(), "Demo: mở camera sẽ có ở bản tích hợp AI thật", Toast.LENGTH_SHORT).show());
        pickGalleryButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        analyzeButton.setOnClickListener(v -> {
            repository.increaseScanCount();
            currentResult = repository.mockScanResult();
            bindResult(currentResult);
            Toast.makeText(requireContext(), "AI đã phân tích xong hình ảnh", Toast.LENGTH_SHORT).show();
        });

        pronounceButton.setOnClickListener(v -> textToSpeech.speak(currentResult.getWord(), TextToSpeech.QUEUE_FLUSH, null, "scan-word"));
        saveButton.setOnClickListener(v -> {
            repository.saveWord(new WordEntry(
                    currentResult.getWord(),
                    currentResult.getIpa(),
                    currentResult.getMeaning(),
                    currentResult.getExample(),
                    currentResult.getCategory()
            ));
            Toast.makeText(requireContext(), "Đã lưu từ scan vào từ điển", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroyView();
    }

    private void handleImagePicked(Uri uri) {
        if (uri != null) {
            previewImage.setImageURI(uri);
            Toast.makeText(requireContext(), "Ảnh đã được chọn", Toast.LENGTH_SHORT).show();
        }
    }

    private void bindResult(ScanResult result) {
        wordText.setText(result.getWord());
        ipaMeaningText.setText(result.getIpa() + " • " + result.getMeaning());
        exampleText.setText(result.getExample());
        categoryText.setText("Danh mục: " + result.getCategory());
        funFactText.setText("Fun fact: " + result.getFunFact());
        relatedText.setText("Từ liên quan: " + result.getRelatedWords().get(0) + ", " + result.getRelatedWords().get(1) + ", " + result.getRelatedWords().get(2));
    }
}
