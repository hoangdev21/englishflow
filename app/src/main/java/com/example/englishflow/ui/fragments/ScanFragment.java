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
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.camera.core.Camera;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
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
import com.example.englishflow.data.DictionaryResult;
import com.example.englishflow.data.FreeDictionaryService;
import com.example.englishflow.data.GeminiVisionService;
import com.example.englishflow.data.NetworkClientProvider;
import com.example.englishflow.data.ScanAnalyzer;
import com.example.englishflow.data.ScanResult;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.views.MagicLensOverlayView;
import com.example.englishflow.ui.viewmodel.ScanViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScanFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final float TTS_PITCH = 1.0f;
    private static final int MAX_GALLERY_IMAGE_DIMENSION = 1280;
    private static final long PREVIEW_HINT_INTERVAL_MS = 4500L;
        private static final long MAGIC_LENS_ANALYSIS_INTERVAL_MS = 260L;
        private static final int MAGIC_LENS_MAX_OVERLAYS = 18;

        private static final Pattern MAGIC_LENS_WORD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z'’-]{2,}");
        private static final Set<String> MAGIC_LENS_COMMON_WORDS = new HashSet<>(Arrays.asList(
            "the", "and", "for", "you", "that", "with", "this", "have", "from", "your",
            "what", "when", "where", "which", "there", "their", "would", "could", "should",
            "about", "after", "before", "because", "into", "than", "then", "them", "they",
            "were", "been", "will", "shall", "while", "also", "just", "such", "some", "more",
            "most", "many", "much", "very", "here", "over", "under", "between", "through",
            "during", "until", "again", "once", "only", "every", "other", "each", "same", "both",
            "book", "page", "line", "text", "read", "reading", "chapter", "story", "paper"
        ));

    private AppRepository repository;
    private AppSettingsStore settingsStore;
    private GeminiVisionService geminiService;
    private FreeDictionaryService freeDictionaryService;
    private TextRecognizer magicLensTextRecognizer;
    private TextToSpeech textToSpeech;
    private ExecutorService imageExecutor;
    private ExecutorService cameraExecutor;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
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
    private View magicLensStatusDot;
    private LinearProgressIndicator scanLoading;
    private MaterialButton magicLensToggleButton;
    private MaterialButton magicLensModeButton;
    private MagicLensOverlayView magicLensOverlay;



    private ScanViewModel scanViewModel;
    private boolean isProcessing = false;
    private boolean isPreviewAnalyzing = false;
    private boolean isMagicLensEnabled = false;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private Handler previewHintHandler;
    private Runnable previewHintRunnable;
    private Camera camera;
    private ScaleGestureDetector scaleGestureDetector;

    private MagicLensOverlayView.LabelMode magicLensLabelMode = MagicLensOverlayView.LabelMode.TRANSLATION;
    private long lastMagicLensAnalysisAt = 0L;
    private final AtomicBoolean magicLensAnalysisRunning = new AtomicBoolean(false);
    private final Map<String, LensWordInfo> magicLensWordCache = new ConcurrentHashMap<>();
    private final Set<String> pendingMagicLensLookups = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> learnedWordSet = Collections.synchronizedSet(new HashSet<>());

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
        freeDictionaryService = new FreeDictionaryService(NetworkClientProvider.getBaseClient());
        magicLensTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
        setupZoomGesture();
        warmMagicLensVocabularyCaches();
        updateMagicLensUi();
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
        magicLensStatusDot = view.findViewById(R.id.magicLensStatusDot);
        scanLoading = view.findViewById(R.id.scanLoading);
        magicLensToggleButton = view.findViewById(R.id.btnMagicLensToggle);
        magicLensModeButton = view.findViewById(R.id.btnMagicLensMode);
        magicLensOverlay = view.findViewById(R.id.magicLensOverlayView);

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
        if (magicLensToggleButton != null) {
            magicLensToggleButton.setOnClickListener(v -> toggleMagicLens());
        }
        if (magicLensModeButton != null) {
            magicLensModeButton.setOnClickListener(v -> cycleMagicLensMode());
        }
        if (magicLensOverlay != null) {
            magicLensOverlay.setOnWordTapListener(this::onMagicLensWordTapped);
            magicLensOverlay.setLensActive(false);
        }
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

                imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeMagicLensFrame);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
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

    private void setupZoomGesture() {
        scaleGestureDetector = new ScaleGestureDetector(requireContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (camera == null) return true;
                float currentZoomRatio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                float delta = detector.getScaleFactor();
                camera.getCameraControl().setZoomRatio(currentZoomRatio * delta);
                return true;
            }
        });

        cameraPreview.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void toggleMagicLens() {
        isMagicLensEnabled = !isMagicLensEnabled;
        if (!isMagicLensEnabled) {
            if (magicLensOverlay != null) {
                magicLensOverlay.clearOverlays();
            }
            updateMagicLensStatus(false);
        } else {
            updateMagicLensStatus(true);
        }
        updateMagicLensUi();
    }

    private void cycleMagicLensMode() {
        if (magicLensLabelMode == MagicLensOverlayView.LabelMode.TRANSLATION) {
            magicLensLabelMode = MagicLensOverlayView.LabelMode.IPA;
        } else if (magicLensLabelMode == MagicLensOverlayView.LabelMode.IPA) {
            magicLensLabelMode = MagicLensOverlayView.LabelMode.TRANSLATION_IPA;
        } else {
            magicLensLabelMode = MagicLensOverlayView.LabelMode.TRANSLATION;
        }

        if (magicLensOverlay != null) {
            magicLensOverlay.setLabelMode(magicLensLabelMode);
        }
        updateMagicLensUi();
    }

    private void updateMagicLensUi() {
        if (magicLensToggleButton != null) {
            magicLensToggleButton.setText(isMagicLensEnabled
                    ? R.string.scan_magic_lens_toggle_off
                    : R.string.scan_magic_lens_toggle_on);
        }
        if (magicLensModeButton != null) {
            magicLensModeButton.setEnabled(isMagicLensEnabled);
            magicLensModeButton.setAlpha(isMagicLensEnabled ? 1f : 0.58f);
            if (magicLensLabelMode == MagicLensOverlayView.LabelMode.IPA) {
                magicLensModeButton.setText(R.string.scan_magic_lens_mode_ipa);
            } else if (magicLensLabelMode == MagicLensOverlayView.LabelMode.TRANSLATION_IPA) {
                magicLensModeButton.setText(R.string.scan_magic_lens_mode_translation_ipa);
            } else {
                magicLensModeButton.setText(R.string.scan_magic_lens_mode_translation);
            }
        }
        if (magicLensOverlay != null) {
            magicLensOverlay.setLensActive(isMagicLensEnabled);
            magicLensOverlay.setLabelMode(magicLensLabelMode);
            if (!isMagicLensEnabled) {
                magicLensOverlay.clearOverlays();
            }
        }
        updateMagicLensStatus(isMagicLensEnabled);
    }

    private void analyzeMagicLensFrame(@NonNull ImageProxy imageProxy) {
        if (!isMagicLensEnabled || magicLensTextRecognizer == null || !isAdded()) {
            imageProxy.close();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastMagicLensAnalysisAt < MAGIC_LENS_ANALYSIS_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        if (!magicLensAnalysisRunning.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            magicLensAnalysisRunning.set(false);
            imageProxy.close();
            return;
        }

        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        final int sourceWidth = (rotation == 90 || rotation == 270) ? imageProxy.getHeight() : imageProxy.getWidth();
        final int sourceHeight = (rotation == 90 || rotation == 270) ? imageProxy.getWidth() : imageProxy.getHeight();
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);

        lastMagicLensAnalysisAt = now;
        magicLensTextRecognizer.process(inputImage)
                .addOnSuccessListener(visionText -> processMagicLensResult(visionText, sourceWidth, sourceHeight))
                .addOnFailureListener(error -> {
                        // magicLensStatusDot remains active state color
                })
                .addOnCompleteListener(task -> {
                    magicLensAnalysisRunning.set(false);
                    imageProxy.close();
                });
    }

    private void processMagicLensResult(@NonNull Text visionText, int sourceWidth, int sourceHeight) {
        if (!isAdded() || !isMagicLensEnabled) {
            return;
        }

        List<MagicLensOverlayView.WordOverlay> overlays = buildMagicLensOverlays(visionText, sourceWidth, sourceHeight);
        if (magicLensOverlay != null) {
            magicLensOverlay.post(() -> {
                if (!isAdded()) {
                    return;
                }
                magicLensOverlay.setWordOverlays(overlays);
            });
        }

        // Status is handled by toggle; no need for real-time text overrides to keep UI clean.
    }

    @NonNull
    private List<MagicLensOverlayView.WordOverlay> buildMagicLensOverlays(@NonNull Text visionText,
                                                                          int sourceWidth,
                                                                          int sourceHeight) {
        List<MagicLensOverlayView.WordOverlay> overlays = new java.util.ArrayList<>();
        Set<String> seen = new HashSet<>();

        outerLoop:
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            if (block == null) {
                continue;
            }
            for (Text.Line line : block.getLines()) {
                if (line == null) {
                    continue;
                }

                List<Text.Element> elements = line.getElements();
                if (elements == null || elements.isEmpty()) {
                    continue;
                }

                for (Text.Element element : elements) {
                    if (element == null) {
                        continue;
                    }
                    Rect rect = element.getBoundingBox();
                    if (rect == null) {
                        continue;
                    }

                    RectF mappedRect = mapBoundingBoxToPreview(rect, sourceWidth, sourceHeight);
                    if (mappedRect == null) {
                        continue;
                    }

                    Matcher matcher = MAGIC_LENS_WORD_PATTERN.matcher(nonNull(element.getText()));
                    while (matcher.find()) {
                        String normalized = normalizeWordForMagicLens(matcher.group());
                        if (!shouldShowInMagicLens(normalized)) {
                            continue;
                        }

                        String key = normalized
                                + ":" + Math.round(mappedRect.left / 10f)
                                + ":" + Math.round(mappedRect.top / 10f);
                        if (!seen.add(key)) {
                            continue;
                        }

                        LensWordInfo info = resolveMagicLensWordInfo(normalized);
                        overlays.add(new MagicLensOverlayView.WordOverlay(
                                normalized,
                                mappedRect,
                                info.ipa,
                                info.meaning,
                                info.confidence
                        ));

                        if (overlays.size() >= MAGIC_LENS_MAX_OVERLAYS) {
                            break outerLoop;
                        }
                    }
                }
            }
        }

        overlays.sort((left, right) -> Integer.compare(
                scoreMagicLensDifficulty(right.word),
                scoreMagicLensDifficulty(left.word)
        ));

        if (overlays.size() > MAGIC_LENS_MAX_OVERLAYS) {
            return new java.util.ArrayList<>(overlays.subList(0, MAGIC_LENS_MAX_OVERLAYS));
        }
        return overlays;
    }

    private int scoreMagicLensDifficulty(@NonNull String word) {
        int score = Math.max(0, word.length());
        if (!learnedWordSet.contains(word)) {
            score += 4;
        }
        if (!MAGIC_LENS_COMMON_WORDS.contains(word)) {
            score += 3;
        }
        if (word.length() >= 8) {
            score += 2;
        }
        return score;
    }

    @Nullable
    private RectF mapBoundingBoxToPreview(@NonNull Rect sourceRect, int sourceWidth, int sourceHeight) {
        if (cameraPreview == null) {
            return null;
        }
        int previewWidth = cameraPreview.getWidth();
        int previewHeight = cameraPreview.getHeight();
        if (previewWidth <= 0 || previewHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return null;
        }

        float scale = Math.max((float) previewWidth / sourceWidth, (float) previewHeight / sourceHeight);
        float scaledWidth = sourceWidth * scale;
        float scaledHeight = sourceHeight * scale;
        float dx = (previewWidth - scaledWidth) / 2f;
        float dy = (previewHeight - scaledHeight) / 2f;

        RectF mapped = new RectF(
                sourceRect.left * scale + dx,
                sourceRect.top * scale + dy,
                sourceRect.right * scale + dx,
                sourceRect.bottom * scale + dy
        );

        mapped.left = Math.max(0f, mapped.left);
        mapped.top = Math.max(0f, mapped.top);
        mapped.right = Math.min(previewWidth, mapped.right);
        mapped.bottom = Math.min(previewHeight, mapped.bottom);

        if (mapped.width() < 18f || mapped.height() < 10f) {
            return null;
        }
        return mapped;
    }

    @NonNull
    private LensWordInfo resolveMagicLensWordInfo(@NonNull String word) {
        LensWordInfo cached = magicLensWordCache.get(word);
        if (cached != null) {
            return cached;
        }

        LensWordInfo placeholder = new LensWordInfo(word, "", "", "", 0.32f);
        magicLensWordCache.put(word, placeholder);
        requestMagicLensWordEnrichment(word);
        return placeholder;
    }

    private void requestMagicLensWordEnrichment(@NonNull String word) {
        if (!pendingMagicLensLookups.add(word)) {
            return;
        }

        imageExecutor.execute(() -> {
            boolean dictionaryRequested = false;
            try {
                CustomVocabularyEntity custom = repository.findCustomVocabulary(word);
                if (custom != null) {
                    cacheMagicLensWordInfo(
                            word,
                            nonNull(custom.ipa),
                            nonNull(custom.meaning),
                            "custom",
                            0.94f
                    );
                }

                repository.fetchVietnameseSuggestion(word, suggestion -> {
                    if (!TextUtils.isEmpty(suggestion)) {
                        cacheMagicLensWordInfo(word, null, suggestion, "translate", 0.66f);
                    }
                });

                LensWordInfo existing = magicLensWordCache.get(word);
                boolean needIpa = existing == null || existing.ipa.isEmpty() || "-".equals(existing.ipa);
                if (needIpa && freeDictionaryService != null) {
                    dictionaryRequested = true;
                    freeDictionaryService.lookupWord(word, new FreeDictionaryService.LookupCallback() {
                        @Override
                        public void onSuccess(DictionaryResult result) {
                            String ipa = result != null ? nonNull(result.getIpa()) : "";
                            cacheMagicLensWordInfo(word, ipa, null, "dictionary", 0.83f);
                            pendingMagicLensLookups.remove(word);
                        }

                        @Override
                        public void onNotFound() {
                            pendingMagicLensLookups.remove(word);
                        }

                        @Override
                        public void onError(Exception exception) {
                            pendingMagicLensLookups.remove(word);
                        }
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (!dictionaryRequested) {
                    pendingMagicLensLookups.remove(word);
                }
            }
        });
    }

    private void cacheMagicLensWordInfo(@NonNull String word,
                                        @Nullable String ipa,
                                        @Nullable String meaning,
                                        @Nullable String source,
                                        float confidence) {
        LensWordInfo current = magicLensWordCache.get(word);

        String nextIpa = current != null ? current.ipa : "";
        String nextMeaning = current != null ? current.meaning : "";
        String nextSource = current != null ? current.source : "";
        float nextConfidence = current != null ? current.confidence : 0.2f;

        String cleanIpa = nonNull(ipa);
        if (!cleanIpa.isEmpty() && !"-".equals(cleanIpa)) {
            nextIpa = cleanIpa;
        }

        String cleanMeaning = nonNull(meaning);
        if (!cleanMeaning.isEmpty()) {
            nextMeaning = cleanMeaning;
        }

        String cleanSource = nonNull(source);
        if (!cleanSource.isEmpty()) {
            nextSource = cleanSource;
        }

        nextConfidence = Math.max(nextConfidence, confidence);

        magicLensWordCache.put(word, new LensWordInfo(word, nextIpa, nextMeaning, nextSource, nextConfidence));
    }

    private void warmMagicLensVocabularyCaches() {
        imageExecutor.execute(() -> {
            try {
                List<WordEntry> savedWords = repository.getSavedWords();
                for (WordEntry wordEntry : savedWords) {
                    String normalized = normalizeWordForMagicLens(wordEntry.getWord());
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    learnedWordSet.add(normalized);
                    cacheMagicLensWordInfo(
                            normalized,
                            nonNull(wordEntry.getIpa()),
                            nonNull(wordEntry.getMeaning()),
                            "saved",
                            0.98f
                    );
                }

                List<CustomVocabularyEntity> customVocabulary = repository.getAllCustomVocabulary();
                for (CustomVocabularyEntity entity : customVocabulary) {
                    String normalized = normalizeWordForMagicLens(entity.word);
                    if (normalized.isEmpty()) {
                        continue;
                    }
                    cacheMagicLensWordInfo(
                            normalized,
                            nonNull(entity.ipa),
                            nonNull(entity.meaning),
                            "custom",
                            0.9f
                    );
                }
            } catch (Exception ignored) {
            }
        });
    }

    @NonNull
    private String normalizeWordForMagicLens(@Nullable String raw) {
        String value = nonNull(raw)
                .replace('’', '\'')
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("^[^a-z']+|[^a-z']+$", "")
                .replaceAll("\\s+", " ");

        if (value.endsWith("'s") && value.length() > 3) {
            value = value.substring(0, value.length() - 2);
        }
        return value;
    }

    private boolean shouldShowInMagicLens(@NonNull String word) {
        if (word.isEmpty() || word.length() < 4) {
            return false;
        }
        if (word.matches(".*\\d.*")) {
            return false;
        }
        if (MAGIC_LENS_COMMON_WORDS.contains(word)) {
            return false;
        }
        if (learnedWordSet.contains(word) && word.length() < 8) {
            return false;
        }
        return true;
    }

    private void onMagicLensWordTapped(@NonNull MagicLensOverlayView.WordOverlay overlay) {
        pronounceMagicLensWord(overlay.word);
        showMagicLensWordSheet(overlay);
    }

    private void pronounceMagicLensWord(@NonNull String word) {
        if (textToSpeech == null) {
            return;
        }
        textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, null, "magic-lens-word");
    }

    private void showMagicLensWordSheet(@NonNull MagicLensOverlayView.WordOverlay overlay) {
        if (!isAdded()) {
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_magic_lens_word, null, false);

        TextView wordView = sheetView.findViewById(R.id.tvMagicLensSheetWord);
        TextView ipaView = sheetView.findViewById(R.id.tvMagicLensSheetIpa);
        TextView meaningView = sheetView.findViewById(R.id.tvMagicLensSheetMeaning);
        MaterialButton speakButton = sheetView.findViewById(R.id.btnMagicLensSheetSpeak);
        MaterialButton saveButton = sheetView.findViewById(R.id.btnMagicLensSheetSave);

        String normalized = normalizeWordForMagicLens(overlay.word);
        LensWordInfo info = magicLensWordCache.get(normalized);
        String finalWord = normalized.isEmpty() ? overlay.word : normalized;
        String finalIpa = info != null && !TextUtils.isEmpty(info.ipa) ? info.ipa : overlay.ipa;
        String finalMeaning = info != null && !TextUtils.isEmpty(info.meaning) ? info.meaning : overlay.meaning;

        if (TextUtils.isEmpty(finalMeaning)) {
            finalMeaning = getString(R.string.scan_magic_lens_fallback_meaning);
        }
        if (TextUtils.isEmpty(finalIpa)) {
            finalIpa = "-";
        }

        wordView.setText(finalWord);
        ipaView.setText(finalIpa);
        meaningView.setText(finalMeaning);

        String meaningToSave = finalMeaning;
        String ipaToSave = finalIpa;

        speakButton.setOnClickListener(v -> pronounceMagicLensWord(finalWord));
        saveButton.setOnClickListener(v -> {
            saveWordFromMagicLens(finalWord, ipaToSave, meaningToSave);
            dialog.dismiss();
        });

        dialog.setContentView(sheetView);
        dialog.show();
    }

    private void saveWordFromMagicLens(@NonNull String word,
                                       @Nullable String ipa,
                                       @Nullable String meaning) {
        String safeWord = normalizeWordForMagicLens(word);
        if (safeWord.isEmpty()) {
            return;
        }

        String safeMeaning = nonNull(meaning);
        if (safeMeaning.isEmpty()) {
            safeMeaning = getString(R.string.scan_magic_lens_fallback_meaning);
        }

        String safeIpa = nonNull(ipa);
        if (safeIpa.isEmpty()) {
            safeIpa = "-";
        }

        repository.saveWord(new WordEntry(
                safeWord,
                safeIpa,
                safeMeaning,
                "noun",
                "I found this word with Magic Lens.",
                "Tôi gặp từ này khi dùng Magic Lens.",
                "",
                "Magic Lens",
                "scan|Saved from realtime overlay"
        ));

        learnedWordSet.add(safeWord);
        cacheMagicLensWordInfo(safeWord, safeIpa, safeMeaning, "saved", 0.99f);
        Toast.makeText(requireContext(), R.string.scan_magic_lens_save_success, Toast.LENGTH_SHORT).show();
    }

    private void updateMagicLensStatus(boolean isActive) {
        if (magicLensStatusDot != null) {
            magicLensStatusDot.post(() -> {
                magicLensStatusDot.setBackgroundResource(isActive
                        ? R.drawable.bg_status_dot_on
                        : R.drawable.bg_status_dot_off);
            });
        }
    }

    @NonNull
    private String nonNull(@Nullable String value) {
        return value == null ? "" : value.trim();
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
                repository.increaseScanCount();
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
                repository.increaseScanCount();
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
                "scan|" + (currentResult.getFunFact() == null ? "" : currentResult.getFunFact())
        ));
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

    private static class LensWordInfo {
        final String word;
        final String ipa;
        final String meaning;
        final String source;
        final float confidence;

        LensWordInfo(String word, String ipa, String meaning, String source, float confidence) {
            this.word = word == null ? "" : word;
            this.ipa = ipa == null ? "" : ipa;
            this.meaning = meaning == null ? "" : meaning;
            this.source = source == null ? "" : source;
            this.confidence = Math.max(0f, Math.min(1f, confidence));
        }
    }

    @Override
    public void onDestroyView() {
        stopPreviewHintLoop();
        isMagicLensEnabled = false;
        if (magicLensOverlay != null) {
            magicLensOverlay.clearOverlays();
        }
        if (magicLensTextRecognizer != null) {
            magicLensTextRecognizer.close();
            magicLensTextRecognizer = null;
        }
        pendingMagicLensLookups.clear();
        magicLensAnalysisRunning.set(false);
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
