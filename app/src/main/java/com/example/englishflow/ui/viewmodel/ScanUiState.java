package com.example.englishflow.ui.viewmodel;

import androidx.annotation.Nullable;

import com.example.englishflow.data.ScanResult;

public class ScanUiState {
    private final ScanResult scanResult;
    private final String rawAiLabel;
    private final String mappedWord;
    private final float confidence;
    private final String previewSuggestion;
    private final boolean loading;
    private final boolean analyzing;
    @Nullable
    private final String message;

    public ScanUiState(ScanResult scanResult,
                       String rawAiLabel,
                       String mappedWord,
                       float confidence,
                       String previewSuggestion,
                       boolean loading,
                       boolean analyzing,
                       @Nullable String message) {
        this.scanResult = scanResult;
        this.rawAiLabel = rawAiLabel;
        this.mappedWord = mappedWord;
        this.confidence = confidence;
        this.previewSuggestion = previewSuggestion;
        this.loading = loading;
        this.analyzing = analyzing;
        this.message = message;
    }

    public ScanResult getScanResult() {
        return scanResult;
    }

    public String getRawAiLabel() {
        return rawAiLabel;
    }

    public String getMappedWord() {
        return mappedWord;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getPreviewSuggestion() {
        return previewSuggestion;
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isAnalyzing() {
        return analyzing;
    }

    @Nullable
    public String getMessage() {
        return message;
    }
}
