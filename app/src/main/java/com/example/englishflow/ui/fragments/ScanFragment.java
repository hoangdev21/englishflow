package com.example.englishflow.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.app.AlertDialog;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.AppSettingsStore;
import com.example.englishflow.data.GeminiVisionService;
import com.example.englishflow.data.ScanAnalyzer;
import com.example.englishflow.data.ScanResult;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.viewmodel.ScanViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final float TTS_PITCH = 1.0f;
    private static final int MAX_GALLERY_IMAGE_DIMENSION = 1280;
    private static final long PREVIEW_HINT_INTERVAL_MS = 4500L;

    private AppRepository repository;
    private AppSettingsStore settingsStore;
    private GeminiVisionService geminiService;
    private TextToSpeech textToSpeech;
    private ExecutorService imageExecutor;
    private ExecutorService cameraExecutor;

    private ImageCapture imageCapture;
    private ActivityResultLauncher<String> pickImageLauncher;

    private PreviewView cameraPreview;
    private TextView wordText;
    private TextView wordTypeText;
    private TextView ipaText;
    private TextView meaningText;
    private TextView exampleText;
    private TextView exampleViText;
    private TextView categoryText;
    private TextView funFactText;
    private TextView relatedText;
    private TextView rawLabelText;
    private TextView mappedWordText;
    private TextView confidenceText;
    private TextView previewHintText;
    private LinearProgressIndicator scanLoading;



    private ScanViewModel scanViewModel;
    private boolean isProcessing = false;
    private boolean isPreviewAnalyzing = false;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private Handler previewHintHandler;
    private Runnable previewHintRunnable;

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
        settingsStore = new AppSettingsStore(requireContext());
        imageExecutor = Executors.newSingleThreadExecutor();
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanViewModel = new ViewModelProvider(this).get(ScanViewModel.class);

        android.util.Log.d("ScanFragment", "Initializing Groq Vision service...");
        try {
            geminiService = new GeminiVisionService();
            android.util.Log.d("ScanFragment", "Groq Vision service initialized successfully");
        } catch (IllegalStateException e) {
            android.util.Log.e("ScanFragment", "Groq Vision init failed: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        analyzeGalleryImage(uri);
                    }
                }
        );

        bindViews(view);
        observeUiState();
        initTextToSpeech();
        setupButtons(view);
        // startPreviewHintLoop(); // Removed to support user's wish for a cleaner preview without real-time overrides

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        // ══ Window Insets Handling (Responsive Status Bar Clearance) ══
        View headerContent = view.findViewById(R.id.scanHeaderContent);
        if (headerContent != null) {
            final int initialLeftPadding = headerContent.getPaddingLeft();
            final int initialTopPadding = headerContent.getPaddingTop();
            final int initialRightPadding = headerContent.getPaddingRight();
            final int initialBottomPadding = headerContent.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(headerContent, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Keep the XML base spacing and only add actual status bar inset.
                v.setPadding(initialLeftPadding, systemBars.top + initialTopPadding, initialRightPadding, initialBottomPadding);
                return windowInsets;
            });
            ViewCompat.requestApplyInsets(headerContent);
        }
    }

    private void bindViews(@NonNull View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        wordText = view.findViewById(R.id.scanWord);
        wordTypeText = view.findViewById(R.id.scanWordType);
        ipaText = view.findViewById(R.id.scanIpa);
        meaningText = view.findViewById(R.id.scanMeaning);
        exampleText = view.findViewById(R.id.scanExample);
        exampleViText = view.findViewById(R.id.scanExampleVi);
        categoryText = view.findViewById(R.id.scanCategory);
        funFactText = view.findViewById(R.id.scanFunFact);
        relatedText = view.findViewById(R.id.scanRelated);
        confidenceText = view.findViewById(R.id.scanConfidence);
        rawLabelText = view.findViewById(R.id.scanRawLabel);
        mappedWordText = view.findViewById(R.id.scanMappedWord);
        previewHintText = view.findViewById(R.id.scanPreviewHint);
        scanLoading = view.findViewById(R.id.scanLoading);

    }

    private void observeUiState() {
        scanViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            setLoading(state.isLoading());
            bindResult(state.getScanResult());
            bindDecisionInfo(state.getRawAiLabel(), state.getMappedWord(), state.getConfidence());
            bindPreviewHint(state.getPreviewSuggestion());
            if (!TextUtils.isEmpty(state.getMessage()) && isAdded()) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
                scanViewModel.clearMessage();
            }
        });
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(settingsStore.getVoiceSpeechRate());
                textToSpeech.setPitch(TTS_PITCH);
            }
        });
    }

    private void setupButtons(@NonNull View view) {
        View takePhotoButton = view.findViewById(R.id.btnTakePhoto);
        MaterialButton pickGalleryButton = view.findViewById(R.id.btnPickGallery);
        MaterialButton analyzeButton = view.findViewById(R.id.btnAnalyzeAi);

        MaterialButton pronounceButton = view.findViewById(R.id.btnPronounceScan);
        MaterialButton saveButton = view.findViewById(R.id.btnSaveScan);
        MaterialButton manageCustomWordsButton = view.findViewById(R.id.btnManageCustomWords);

        takePhotoButton.setVisibility(View.VISIBLE);
        pickGalleryButton.setVisibility(View.VISIBLE);

        takePhotoButton.setOnClickListener(v -> capturePhoto());
        pickGalleryButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        analyzeButton.setOnClickListener(v -> toggleCamera());
        pronounceButton.setOnClickListener(v -> pronounceWord());
        saveButton.setOnClickListener(v -> saveCurrentWord());
        manageCustomWordsButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.example.englishflow.ui.CustomVocabularyManagerActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (isAdded()) {
            Toast.makeText(requireContext(), "Camera permission needed", Toast.LENGTH_LONG).show();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Camera failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void toggleCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        startCamera();
    }

    private void analyzeGalleryImage(@NonNull Uri imageUri) {
        if (isProcessing) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Analyzing... please wait", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        scanViewModel.startAnalysis(true);
        isProcessing = true;

        imageExecutor.execute(() -> {
            try {
                Bitmap bitmap = loadBitmapFromUri(imageUri);
                ImageQualityCheck qualityCheck = evaluateImageQuality(bitmap);
                if (!qualityCheck.isAcceptable()) {
                    scanViewModel.failAnalysis(qualityCheck.getMessage());
                    isProcessing = false;
                    return;
                }
                analyzeImageWithGemini(bitmap);
            } catch (Exception e) {
                scanViewModel.failAnalysis("Error: " + e.getMessage());
                isProcessing = false;
            }
        });
    }

    private void capturePhoto() {
        if (isProcessing) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Analyzing... please wait", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (imageCapture == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        scanViewModel.startAnalysis(true);
        isProcessing = true;

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    Image mediaImage = image.getImage();
                    if (mediaImage == null) {
                        image.close();
                        scanViewModel.failAnalysis("Cannot read image");
                        isProcessing = false;
                        return;
                    }

                    Bitmap bitmap = imageToBitmap(image);
                    image.close();

                    if (bitmap == null) {
                        scanViewModel.failAnalysis("Cannot convert image");
                        isProcessing = false;
                        return;
                    }

                    ImageQualityCheck qualityCheck = evaluateImageQuality(bitmap);
                    if (!qualityCheck.isAcceptable()) {
                        scanViewModel.failAnalysis(qualityCheck.getMessage());
                        isProcessing = false;
                        return;
                    }

                    analyzeImageWithGemini(bitmap);
                } catch (Exception e) {
                    image.close();
                    scanViewModel.failAnalysis("Error: " + e.getMessage());
                    isProcessing = false;
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                scanViewModel.failAnalysis("Capture error");
                isProcessing = false;
            }
        });
    }

    private void analyzeImageWithGemini(@NonNull Bitmap bitmap) {
        try {
            android.util.Log.d("ScanFragment", "Starting Groq full image analysis...");
            ScanResult result = geminiService.analyzeImageFull(bitmap);
            android.util.Log.d("ScanFragment", "Groq returned full result for: " + result.getWord());
            
            new Handler(Looper.getMainLooper()).post(() -> {
                scanViewModel.completeAnalysis(result, result.getWord(), result.getWord(), 0.98f, "Analysis complete");
                isProcessing = false;
            });
        } catch (Exception e) {
            android.util.Log.e("ScanFragment", "Groq error: " + e.getMessage(), e);
            String message = e.getMessage() == null ? "Unknown error" : e.getMessage();
            scanViewModel.failAnalysis("Groq error: " + message);
            isProcessing = false;
        }
    }

    private void processDetectedLabel(@NonNull String rawLabel, float confidence) {
        try {
            android.util.Log.d("ScanFragment", "Processing raw label: '" + rawLabel + "'");
            String canonical = ScanAnalyzer.canonicalizeLabel(rawLabel);
            android.util.Log.d("ScanFragment", "Canonical label: '" + canonical + "'");

            // Check if we have custom vocabulary for this
            CustomVocabularyEntity custom = repository.findCustomVocabulary(canonical);
            if (custom != null && !TextUtils.isEmpty(custom.meaning)) {
                android.util.Log.d("ScanFragment", "Found custom vocabulary: " + custom.meaning);
                ScanResult result = buildCustomScanResult(canonical, custom.meaning);
                scanViewModel.completeAnalysis(result, rawLabel, result.getWord(), confidence, "Word found");
                isProcessing = false;
                return;
            }

            // Try static mapping
            android.util.Log.d("ScanFragment", "Looking up static mapping for: " + canonical);
            ScanResult staticResult = ScanAnalyzer.fromDetectedLabel(canonical);
            android.util.Log.d("ScanFragment", "Static result category: " + (staticResult != null ? staticResult.getCategory() : "null"));

            // If AI cannot recognize object, show a clear user-facing message.
            boolean unrecognized = "object".equalsIgnoreCase(canonical)
                    || "unknown".equalsIgnoreCase(canonical)
                    || "Can bo sung".equalsIgnoreCase(staticResult != null ? staticResult.getCategory() : "");
            if (unrecognized) {
                ScanResult fallback = ScanAnalyzer.fallbackResult("object");
                scanViewModel.completeAnalysis(
                        fallback,
                        rawLabel,
                        fallback.getWord(),
                    confidence,
                        "Khong nhan dang duoc vat the. Vui long chup ro hon hoac doi goc khac."
                );
                isProcessing = false;
                return;
            }

            android.util.Log.d("ScanFragment", "Word detected: " + (staticResult != null ? staticResult.getWord() : "null"));
            scanViewModel.completeAnalysis(
                    staticResult,
                    rawLabel,
                    staticResult != null ? staticResult.getWord() : "object",
                    confidence,
                    "Word detected"
            );
            isProcessing = false;
        } catch (Exception e) {
            android.util.Log.e("ScanFragment", "Error processing detected label: " + e.getMessage(), e);
            scanViewModel.failAnalysis("Processing error: " + e.getMessage());
            isProcessing = false;
        }
    }

    private void showAddVocabularyDialog(@NonNull String canonicalLabel,
                                         @Nullable String suggestedMeaning,
                                         @NonNull MeaningSaveCallback saveCallback,
                                         @NonNull Runnable cancelCallback) {
        if (!isAdded()) {
            cancelCallback.run();
            return;
        }

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Enter Vietnamese meaning for: " + canonicalLabel);
        if (!TextUtils.isEmpty(suggestedMeaning)) {
            input.setText(suggestedMeaning);
            input.setSelection(suggestedMeaning.length());
        }
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16,
                requireContext().getResources().getDisplayMetrics()
        );
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add New Word")
                .setMessage("This word is not in dictionary. Add Vietnamese meaning:")
                .setView(input)
                .setNegativeButton("Skip", (dialog, which) -> cancelCallback.run())
                .setPositiveButton("Save", (dialog, which) -> {
                    String meaning = input.getText() == null ? "" : input.getText().toString().trim();
                    if (meaning.isEmpty()) {
                        Toast.makeText(requireContext(), "Meaning cannot be empty", Toast.LENGTH_SHORT).show();
                        cancelCallback.run();
                        return;
                    }
                    saveCallback.onSave(meaning);
                })
                .show();
    }

    private ScanResult buildCustomScanResult(@NonNull String englishWord, @NonNull String meaning) {
        try {
            android.util.Log.d("ScanFragment", "Building custom scan result for: " + englishWord + " -> " + meaning);
            ScanResult result = new ScanResult(
                    englishWord,
                    "-",
                    meaning,
                    "noun",
                    "A manually saved word.",
                    "Một từ vựng được lưu thủ công.",
                    "Custom",
                    "Từ vựng này được học thông qua tính năng trợ lý thị giác.",
                    java.util.Arrays.asList("learn", "vocabulary", "custom")
            );
            android.util.Log.d("ScanFragment", "Custom scan result built successfully");
            return result;
        } catch (Exception e) {
            android.util.Log.e("ScanFragment", "Error building custom scan result: " + e.getMessage(), e);
            throw new RuntimeException("Error building custom result: " + e.getMessage(), e);
        }
    }

    private void bindResult(@Nullable ScanResult result) {
        if (result == null) {
            return;
        }

        wordText.setText(result.getWord());
        if (wordTypeText != null) wordTypeText.setText(result.getWordType());
        if (ipaText != null) ipaText.setText(result.getIpa());
        if (meaningText != null) meaningText.setText(result.getMeaning());
        
        exampleText.setText(result.getExample());
        if (exampleViText != null) exampleViText.setText(result.getExampleVi());
        
        categoryText.setText("Category: " + result.getCategory());
        funFactText.setText(result.getFunFact());
        relatedText.setText(formatRelatedWords(result.getRelatedWords()));
    }

    private void bindDecisionInfo(@Nullable String rawAiLabel, @Nullable String mappedWord, float confidence) {
        rawLabelText.setText("Detected: " + (TextUtils.isEmpty(rawAiLabel) ? "object" : rawAiLabel));
        mappedWordText.setText("Tu da map: " + (TextUtils.isEmpty(mappedWord) ? "object" : mappedWord));
        confidenceText.setText("Do tin cay: " + formatConfidence(confidence));
    }

    private void bindPreviewHint(@Nullable String previewSuggestion) {
        if (previewHintText == null) {
            return;
        }
        previewHintText.setText(TextUtils.isEmpty(previewSuggestion)
                ? "Goi y realtime: huong camera vao vat the"
                : previewSuggestion);
    }

    private void pronounceWord() {
        ScanResult currentResult = scanViewModel.getCurrentResult();
        if (currentResult != null && textToSpeech != null) {
            textToSpeech.speak(currentResult.getWord(), TextToSpeech.QUEUE_FLUSH, null, "scan-word");
        }
    }

    private void saveCurrentWord() {
        ScanResult currentResult = scanViewModel.getCurrentResult();
        if (currentResult == null) {
            return;
        }

        repository.saveWord(new WordEntry(
                currentResult.getWord(),
                currentResult.getIpa(),
                currentResult.getMeaning(),
                currentResult.getWordType(),
                currentResult.getExample(),
                currentResult.getExampleVi(),
                "",
                currentResult.getCategory(),
                currentResult.getFunFact()
        ));
        repository.increaseScanCount();
        Toast.makeText(requireContext(), "Word saved", Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean isLoading) {
        scanLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private String formatRelatedWords(java.util.List<String> relatedWords) {
        if (relatedWords == null || relatedWords.isEmpty()) {
            return "none";
        }
        return TextUtils.join(", ", relatedWords);
    }

    private Bitmap loadBitmapFromUri(@NonNull Uri imageUri) throws IOException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;

        try (InputStream boundsStream = requireContext().getContentResolver().openInputStream(imageUri)) {
            if (boundsStream == null) {
                throw new IOException("Cannot open selected image");
            }
            BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                MAX_GALLERY_IMAGE_DIMENSION,
                MAX_GALLERY_IMAGE_DIMENSION
        );

        Bitmap bitmap;
        try (InputStream imageStream = requireContext().getContentResolver().openInputStream(imageUri)) {
            if (imageStream == null) {
                throw new IOException("Cannot open selected image");
            }
            bitmap = BitmapFactory.decodeStream(imageStream, null, decodeOptions);
        }

        if (bitmap == null) {
            throw new IOException("Cannot decode selected image");
        }
        return bitmap;
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }

        // With ImageCapture callbacks, device output is commonly JPEG (single plane).
        if (planes.length == 1) {
            java.nio.ByteBuffer buffer = planes[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }

        // Fallback for multi-plane formats.
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = planes[0].getPixelStride();
        int outputStride = Math.max(1, pixelStride);
        byte[] buffer = new byte[outputStride * height * width];
        long pixelCount = (long) width * height;

        java.nio.ByteBuffer yBuffer = planes[0].getBuffer();
        yBuffer.rewind();
        int copyLength = Math.min(buffer.length, yBuffer.remaining());
        yBuffer.get(buffer, 0, copyLength);

        int[] rgb = new int[(int) pixelCount];
        for (int i = 0; i < (int) pixelCount; i++) {
            int idx = i * outputStride;
            int y = idx < buffer.length ? (buffer[idx] & 0xFF) : 0;
            rgb[i] = 0xFF000000 | (y << 16) | (y << 8) | y;
        }

        return Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888);
    }

    private int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private void startPreviewHintLoop() {
        if (previewHintHandler != null) {
            return;
        }

        previewHintHandler = new Handler(Looper.getMainLooper());
        previewHintRunnable = new Runnable() {
            @Override
            public void run() {
                tryRunRealtimePreviewSuggestion();
                if (previewHintHandler != null) {
                    previewHintHandler.postDelayed(this, PREVIEW_HINT_INTERVAL_MS);
                }
            }
        };
        previewHintHandler.postDelayed(previewHintRunnable, 1800L);
    }

    private void stopPreviewHintLoop() {
        if (previewHintHandler != null && previewHintRunnable != null) {
            previewHintHandler.removeCallbacks(previewHintRunnable);
        }
        previewHintRunnable = null;
        previewHintHandler = null;
    }

    private void tryRunRealtimePreviewSuggestion() {
        if (!isAdded() || isProcessing || isPreviewAnalyzing || geminiService == null || cameraPreview == null) {
            return;
        }

        Bitmap previewBitmap = cameraPreview.getBitmap();
        if (previewBitmap == null) {
            return;
        }

        ImageQualityCheck qualityCheck = evaluateImageQuality(previewBitmap);
        if (!qualityCheck.isAcceptable()) {
            scanViewModel.updatePreviewSuggestion("Goi y realtime: anh dang mo/toi, hay dua camera gan vat the");
            return;
        }

        isPreviewAnalyzing = true;
        imageExecutor.execute(() -> {
            try {
                GeminiVisionService.VisionResult preview = geminiService.analyzePreviewVietnamese(previewBitmap);
                int percent = Math.round(preview.getConfidence() * 100f);
                String source = preview.isFromCache() ? " - cache" : "";
                String suggestion = "Goi y realtime: " + preview.getPrimaryLabel() + " (" + percent + "%)" + source;
                scanViewModel.updatePreviewSuggestion(suggestion);
            } catch (Exception e) {
                android.util.Log.w("ScanFragment", "Realtime preview suggestion failed: " + e.getMessage());
            } finally {
                isPreviewAnalyzing = false;
            }
        });
    }

    private String formatConfidence(float confidence) {
        int percent = Math.round(Math.max(0f, Math.min(1f, confidence)) * 100f);
        if (percent >= 80) {
            return percent + "% (Cao)";
        }
        if (percent >= 60) {
            return percent + "% (Trung binh)";
        }
        return percent + "% (Thap)";
    }

    private ImageQualityCheck evaluateImageQuality(@NonNull Bitmap source) {
        Bitmap sample = Bitmap.createScaledBitmap(source, 64, 64, true);
        int width = sample.getWidth();
        int height = sample.getHeight();
        int total = width * height;

        float sum = 0f;
        float sumSq = 0f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = sample.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                float luma = (0.299f * r) + (0.587f * g) + (0.114f * b);
                sum += luma;
                sumSq += luma * luma;
            }
        }

        float mean = sum / total;
        float variance = (sumSq / total) - (mean * mean);

        if (mean < 45f) {
            return new ImageQualityCheck(false, "Anh qua toi. Vui long tang anh sang hoac doi goc chup.");
        }
        if (mean > 225f) {
            return new ImageQualityCheck(false, "Anh qua sang. Vui long giam anh sang va chup lai.");
        }
        if (variance < 180f) {
            return new ImageQualityCheck(false, "Anh bi mo, vui long giu may on dinh va chup ro hon.");
        }

        return new ImageQualityCheck(true, "OK");
    }

    private static class ImageQualityCheck {
        private final boolean acceptable;
        private final String message;

        private ImageQualityCheck(boolean acceptable, String message) {
            this.acceptable = acceptable;
            this.message = message;
        }

        private boolean isAcceptable() {
            return acceptable;
        }

        private String getMessage() {
            return message;
        }
    }

    private interface MeaningSaveCallback {
        void onSave(String meaning);
    }

    @Override
    public void onDestroyView() {
        stopPreviewHintLoop();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        if (imageExecutor != null) {
            imageExecutor.shutdown();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroyView();
    }
}
