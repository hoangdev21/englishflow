package com.example.englishflow.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ScrollView;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.app.AlertDialog;

import com.example.englishflow.R;
import com.example.englishflow.data.AppRepository;
import com.example.englishflow.data.ScanAnalyzer;
import com.example.englishflow.data.ScanLabelFusion;
import com.example.englishflow.data.ScanLabelFusion.Candidate;
import com.example.englishflow.data.ScanResult;
import com.example.englishflow.data.WordEntry;
import com.example.englishflow.database.entity.CustomVocabularyEntity;
import com.example.englishflow.ui.viewmodel.ScanViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final long ANALYSIS_INTERVAL_MS = 500;
    private static final int MAX_GALLERY_IMAGE_DIMENSION = 1280;

    private AppRepository repository;
    private TextToSpeech textToSpeech;
    private ExecutorService imageExecutor;
    private ExecutorService cameraExecutor;

    private ObjectDetector streamObjectDetector;
    private ObjectDetector singleImageObjectDetector;
    private ImageLabeler streamImageLabeler;
    private ImageLabeler singleImageLabeler;

    private ImageCapture imageCapture;
    private ActivityResultLauncher<String> pickImageLauncher;

    private PreviewView cameraPreview;
    private TextView wordText;
    private TextView rawLabelText;
    private TextView mappedWordText;
    private TextView ipaMeaningText;
    private TextView exampleText;
    private TextView categoryText;
    private TextView funFactText;
    private TextView relatedText;
    private CircularProgressIndicator scanLoading;

    private ScanViewModel scanViewModel;
    private long lastAnalysisTime = 0;
    private boolean isSelectionDialogShowing = false;

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
        imageExecutor = Executors.newSingleThreadExecutor();
        cameraExecutor = Executors.newSingleThreadExecutor();
        scanViewModel = new ViewModelProvider(this).get(ScanViewModel.class);

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
        initVisionClients();
        initTextToSpeech();
        setupButtons(view);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void bindViews(@NonNull View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        wordText = view.findViewById(R.id.scanWord);
        rawLabelText = view.findViewById(R.id.scanRawLabel);
        mappedWordText = view.findViewById(R.id.scanMappedWord);
        ipaMeaningText = view.findViewById(R.id.scanIpaMeaning);
        exampleText = view.findViewById(R.id.scanExample);
        categoryText = view.findViewById(R.id.scanCategory);
        funFactText = view.findViewById(R.id.scanFunFact);
        relatedText = view.findViewById(R.id.scanRelated);
        scanLoading = view.findViewById(R.id.scanLoading);
    }

    private void observeUiState() {
        scanViewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            setLoading(state.isLoading());
            bindResult(state.getScanResult());
            bindDecisionInfo(state.getRawAiLabel(), state.getMappedWord());
            if (!TextUtils.isEmpty(state.getMessage()) && isAdded()) {
                Toast.makeText(requireContext(), state.getMessage(), Toast.LENGTH_SHORT).show();
                scanViewModel.clearMessage();
            }
        });
    }

    private void initVisionClients() {
        ObjectDetectorOptions streamOptions = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();

        ObjectDetectorOptions singleOptions = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();

        ImageLabelerOptions streamLabelOptions =
                new ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build();
        ImageLabelerOptions singleLabelOptions =
                new ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build();

        streamObjectDetector = ObjectDetection.getClient(streamOptions);
        singleImageObjectDetector = ObjectDetection.getClient(singleOptions);
        streamImageLabeler = ImageLabeling.getClient(streamLabelOptions);
        singleImageLabeler = ImageLabeling.getClient(singleLabelOptions);
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void setupButtons(@NonNull View view) {
        MaterialButton takePhotoButton = view.findViewById(R.id.btnTakePhoto);
        MaterialButton pickGalleryButton = view.findViewById(R.id.btnPickGallery);
        MaterialButton analyzeButton = view.findViewById(R.id.btnAnalyzeAi);
        MaterialButton pronounceButton = view.findViewById(R.id.btnPronounceScan);
        MaterialButton saveButton = view.findViewById(R.id.btnSaveScan);
        MaterialButton manageCustomWordsButton = view.findViewById(R.id.btnManageCustomWords);

        takePhotoButton.setVisibility(View.VISIBLE);
        pickGalleryButton.setVisibility(View.VISIBLE);
        analyzeButton.setVisibility(View.GONE);

        takePhotoButton.setOnClickListener(v -> capturePhoto());
        pickGalleryButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
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
            Toast.makeText(requireContext(), "Can cap quyen camera de su dung", Toast.LENGTH_LONG).show();
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

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
            } catch (Exception e) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Khong the khoi dong camera", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void analyzeGalleryImage(@NonNull Uri imageUri) {
        if (isSelectionDialogShowing) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Hay hoan tat lua chon vat the hien tai", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!scanViewModel.startAnalysis(true)) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Dang phan tich, vui long thu lai", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        imageExecutor.execute(() -> {
            try {
                InputImage inputImage = buildInputImageFromGallery(imageUri);
                resolveBestLabel(inputImage, ScanLabelFusion.Mode.GALLERY)
                        .addOnSuccessListener(decision -> {
                            handleManualSelection(decision, "Da chon vat the tu thu vien");
                        })
                        .addOnFailureListener(e -> scanViewModel.failAnalysis("Loi phan tich anh thu vien"));
            } catch (Exception e) {
                scanViewModel.failAnalysis("Khong doc duoc anh tu thu vien");
            }
        });
    }

    private void capturePhoto() {
        if (isSelectionDialogShowing) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Hay hoan tat lua chon vat the hien tai", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (imageCapture == null) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Camera chua san sang", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!scanViewModel.startAnalysis(true)) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Dang phan tich, vui long thu lai", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    Image mediaImage = image.getImage();
                    if (mediaImage == null) {
                        image.close();
                        scanViewModel.failAnalysis("Khong doc duoc anh chup");
                        return;
                    }

                    InputImage inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            image.getImageInfo().getRotationDegrees()
                    );

                    resolveBestLabel(inputImage, ScanLabelFusion.Mode.CAPTURE)
                            .addOnSuccessListener(decision -> {
                                handleManualSelection(decision, "Da chon vat the tu anh chup");
                            })
                            .addOnFailureListener(e -> scanViewModel.failAnalysis("Loi phan tich anh chup"))
                            .addOnCompleteListener(task -> image.close());
                } catch (Exception e) {
                    image.close();
                    scanViewModel.failAnalysis("Loi khi chup anh");
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                scanViewModel.failAnalysis("Loi chup anh: " + exception.getMessage());
            }
        });
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        // Realtime preview is kept for framing only; vocabulary updates happen after user capture/select.
        image.close();
    }

    private Task<LabelDecision> resolveBestLabel(@NonNull InputImage inputImage, @NonNull ScanLabelFusion.Mode mode) {
        boolean singleImageMode = mode != ScanLabelFusion.Mode.REALTIME;
        ObjectDetector objectDetector = singleImageMode ? singleImageObjectDetector : streamObjectDetector;
        ImageLabeler imageLabeler = singleImageMode ? singleImageLabeler : streamImageLabeler;

        Task<List<DetectedObject>> objectTask = objectDetector.process(inputImage);
        Task<List<ImageLabel>> labelTask = imageLabeler.process(inputImage);

        return Tasks.whenAllSuccess(objectTask, labelTask)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().size() < 2) {
                        return new LabelDecision("object", "object", new ArrayList<>());
                    }

                    @SuppressWarnings("unchecked")
                    List<DetectedObject> objects = (List<DetectedObject>) task.getResult().get(0);
                    @SuppressWarnings("unchecked")
                    List<ImageLabel> labels = (List<ImageLabel>) task.getResult().get(1);

                    Candidate objectCandidate = extractObjectCandidate(objects);
                    Candidate imageCandidate = extractImageCandidate(labels);
                    String finalLabel = ScanLabelFusion.chooseBestLabel(
                            objectCandidate,
                            imageCandidate,
                            mode,
                            ScanAnalyzer.hasSpecificMapping(objectCandidate.getLabel()),
                            ScanAnalyzer.hasSpecificMapping(imageCandidate.getLabel())
                    );
                    String rawLabel = !"object".equals(imageCandidate.getLabel())
                            ? imageCandidate.getLabel()
                            : objectCandidate.getLabel();
                    List<LabelOption> selectableLabels = buildSelectableLabels(objectCandidate, imageCandidate, labels, finalLabel);
                    return new LabelDecision(rawLabel, finalLabel, selectableLabels);
                });
    }

    private void handleManualSelection(@NonNull LabelDecision decision, @NonNull String successMessage) {
        ScanResult current = scanViewModel.getCurrentResult();
        scanViewModel.completeAnalysis(current, decision.rawLabel, current.getWord(), null);

        if (decision.selectableLabels.isEmpty()) {
            ScanResult mapped = ScanAnalyzer.fromDetectedLabel(decision.finalLabel);
            scanViewModel.completeAnalysis(mapped, decision.rawLabel, mapped.getWord(), successMessage);
            return;
        }

        showObjectSelectionGrid(decision.selectableLabels, selectedLabel -> {
            resolveSelectedLabel(selectedLabel, successMessage);
        });
    }

    private void resolveSelectedLabel(@NonNull String selectedLabel, @NonNull String successMessage) {
        String canonical = ScanAnalyzer.canonicalizeLabel(selectedLabel);
        CustomVocabularyEntity custom = repository.findCustomVocabulary(canonical);
        if (custom != null && !TextUtils.isEmpty(custom.meaning)) {
            ScanResult mapped = buildCustomScanResult(canonical, custom.meaning);
            scanViewModel.completeAnalysis(mapped, selectedLabel, mapped.getWord(), successMessage);
            return;
        }

        ScanResult staticResult = ScanAnalyzer.fromDetectedLabel(canonical);
        if ("Can bo sung".equalsIgnoreCase(staticResult.getCategory())) {
            repository.logFailedLabel(canonical);
            repository.fetchVietnameseSuggestion(canonical, suggestion -> showAddVocabularyDialog(
                    canonical,
                    suggestion,
                    meaning -> {
                        repository.saveCustomVocabulary(canonical, meaning);
                        repository.markFailedLabelResolved(canonical);
                        ScanResult mapped = buildCustomScanResult(canonical, meaning);
                        scanViewModel.completeAnalysis(mapped, selectedLabel, mapped.getWord(), "Da luu tu moi");
                    },
                    () -> scanViewModel.completeAnalysis(staticResult, selectedLabel, staticResult.getWord(), successMessage)
            ));
            return;
        }

        scanViewModel.completeAnalysis(staticResult, selectedLabel, staticResult.getWord(), successMessage);
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
        input.setHint("Nhap nghia tieng Viet cho: " + canonicalLabel);
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
                .setTitle("Bo sung tu moi")
            .setMessage(TextUtils.isEmpty(suggestedMeaning)
                ? "Tu '" + canonicalLabel + "' chua co trong tu dien. Hay nhap nghia de luu lai."
                : "Tu '" + canonicalLabel + "' chua co trong tu dien. Da lay goi y online, ban co the sua truoc khi luu.")
                .setView(input)
                .setNegativeButton("Bo qua", (dialog, which) -> cancelCallback.run())
                .setPositiveButton("Luu", (dialog, which) -> {
                    String meaning = input.getText() == null ? "" : input.getText().toString().trim();
                    if (meaning.isEmpty()) {
                        Toast.makeText(requireContext(), "Nghia khong duoc de trong", Toast.LENGTH_SHORT).show();
                        cancelCallback.run();
                        return;
                    }
                    saveCallback.onSave(meaning);
                })
                .show();
    }

    private ScanResult buildCustomScanResult(@NonNull String englishWord, @NonNull String meaning) {
        return new ScanResult(
                englishWord,
                "-",
                meaning,
                "This meaning was added by user.",
                "Tu bo sung",
                "Ban co the chinh sua nghia nay bat cu luc nao.",
                java.util.Arrays.asList("custom", "user", "vocabulary")
        );
    }

    private List<LabelOption> buildSelectableLabels(@NonNull Candidate objectCandidate,
                                                    @NonNull Candidate imageCandidate,
                                                    @NonNull List<ImageLabel> labels,
                                                    @NonNull String finalLabel) {
        Map<String, Float> scoreMap = new HashMap<>();
        putMaxScore(scoreMap, finalLabel, Math.max(objectCandidate.getConfidence(), imageCandidate.getConfidence()));
        putMaxScore(scoreMap, imageCandidate.getLabel(), imageCandidate.getConfidence());
        putMaxScore(scoreMap, objectCandidate.getLabel(), objectCandidate.getConfidence());

        float imageThreshold = ScanLabelFusion.getImageThreshold(ScanLabelFusion.Mode.CAPTURE);
        for (ImageLabel label : labels) {
            if (label.getConfidence() >= imageThreshold) {
                String normalized = ScanLabelFusion.normalize(label.getText());
                putMaxScore(scoreMap, normalized, label.getConfidence());
            }
            if (scoreMap.size() >= 8) {
                break;
            }
        }

        scoreMap.remove("object");
        List<LabelOption> items = new ArrayList<>();
        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            items.add(new LabelOption(entry.getKey(), entry.getValue()));
        }
        items.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        if (items.isEmpty()) {
            items.add(new LabelOption("object", 0f));
        }
        return items;
    }

    private void putMaxScore(@NonNull Map<String, Float> scoreMap, @NonNull String label, float score) {
        String normalized = ScanLabelFusion.normalize(label);
        if (normalized.isEmpty()) {
            return;
        }
        Float current = scoreMap.get(normalized);
        if (current == null || score > current) {
            scoreMap.put(normalized, score);
        }
    }

    private void showObjectSelectionGrid(@NonNull List<LabelOption> labels,
                                         @NonNull LabelSelectionCallback callback) {
        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {
            isSelectionDialogShowing = true;

            int margin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8,
                    requireContext().getResources().getDisplayMetrics()
            );

            GridLayout grid = new GridLayout(requireContext());
            grid.setColumnCount(2);
            grid.setUseDefaultMargins(true);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle("Chon vat the muon dich")
                    .setNegativeButton("Huy", (d, which) -> {
                        isSelectionDialogShowing = false;
                        scanViewModel.failAnalysis("Da huy chon vat the");
                    })
                    .create();

            for (LabelOption option : labels) {
                MaterialButton button = new MaterialButton(requireContext(), null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                button.setText(buildVietnameseGridLabel(option.label) + "\n" + Math.round(option.confidence * 100) + "%");
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = 0;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                lp.setMargins(margin, margin, margin, margin);
                button.setLayoutParams(lp);
                button.setOnClickListener(v -> {
                    isSelectionDialogShowing = false;
                    dialog.dismiss();
                    callback.onSelected(option.label);
                });
                grid.addView(button);
            }

            ScrollView scrollView = new ScrollView(requireContext());
            int padding = margin * 2;
            scrollView.setPadding(padding, padding, padding, padding);
            scrollView.addView(grid);
            dialog.setView(scrollView);
            dialog.setOnDismissListener(d -> isSelectionDialogShowing = false);
            dialog.show();
        });
    }

    private String buildVietnameseGridLabel(@NonNull String rawLabel) {
        String canonical = ScanAnalyzer.canonicalizeLabel(rawLabel);
        String vi = ScanAnalyzer.toVietnameseLabel(canonical);
        return vi + " (" + canonical + ")";
    }

    private Candidate extractObjectCandidate(@NonNull List<DetectedObject> objects) {
        if (objects.isEmpty() || objects.get(0).getLabels().isEmpty()) {
            return new Candidate("object", 0f);
        }

        DetectedObject.Label label = objects.get(0).getLabels().get(0);
        return new Candidate(label.getText(), label.getConfidence());
    }

    private Candidate extractImageCandidate(@NonNull List<ImageLabel> labels) {
        if (labels.isEmpty()) {
            return new Candidate("object", 0f);
        }

        ImageLabel best = labels.get(0);
        for (ImageLabel label : labels) {
            if (label.getConfidence() > best.getConfidence()) {
                best = label;
            }
        }

        return new Candidate(best.getText(), best.getConfidence());
    }

    private InputImage buildInputImageFromGallery(@NonNull Uri imageUri) throws IOException {
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
        return InputImage.fromBitmap(bitmap, 0);
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

    private void bindResult(@Nullable ScanResult result) {
        if (result == null) {
            return;
        }

        wordText.setText(result.getWord());
        ipaMeaningText.setText(result.getIpa() + " - " + result.getMeaning());
        exampleText.setText(result.getExample());
        categoryText.setText("Danh muc: " + result.getCategory());
        funFactText.setText("Fun fact: " + result.getFunFact());
        relatedText.setText("Tu lien quan: " + formatRelatedWords(result.getRelatedWords()));
    }

    private void bindDecisionInfo(@Nullable String rawAiLabel, @Nullable String mappedWord) {
        rawLabelText.setText("Nhan tho AI: " + (TextUtils.isEmpty(rawAiLabel) ? "object" : rawAiLabel));
        mappedWordText.setText("Tu da map: " + (TextUtils.isEmpty(mappedWord) ? "object" : mappedWord));
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
                currentResult.getExample(),
                currentResult.getCategory()
        ));
        repository.increaseScanCount();
        Toast.makeText(requireContext(), getString(R.string.scan_saved), Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean isLoading) {
        scanLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private String formatRelatedWords(List<String> relatedWords) {
        if (relatedWords == null || relatedWords.isEmpty()) {
            return "Chua co tu lien quan";
        }
        return TextUtils.join(", ", relatedWords);
    }

    private static final class LabelDecision {
        private final String rawLabel;
        private final String finalLabel;
        private final List<LabelOption> selectableLabels;

        private LabelDecision(String rawLabel, String finalLabel, List<LabelOption> selectableLabels) {
            this.rawLabel = rawLabel;
            this.finalLabel = finalLabel;
            this.selectableLabels = selectableLabels;
        }
    }

    private static final class LabelOption {
        private final String label;
        private final float confidence;

        private LabelOption(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    private interface LabelSelectionCallback {
        void onSelected(String label);
    }

    private interface MeaningSaveCallback {
        void onSave(String meaning);
    }

    @Override
    public void onDestroyView() {
        if (streamObjectDetector != null) {
            streamObjectDetector.close();
            streamObjectDetector = null;
        }
        if (singleImageObjectDetector != null) {
            singleImageObjectDetector.close();
            singleImageObjectDetector = null;
        }
        if (streamImageLabeler != null) {
            streamImageLabeler.close();
            streamImageLabeler = null;
        }
        if (singleImageLabeler != null) {
            singleImageLabeler.close();
            singleImageLabeler = null;
        }

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
