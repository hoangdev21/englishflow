package com.example.englishflow.ui.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.englishflow.data.ScanAnalyzer;
import com.example.englishflow.data.ScanResult;

import java.util.concurrent.atomic.AtomicBoolean;

public class ScanViewModel extends ViewModel {
    private final MutableLiveData<ScanUiState> uiState = new MutableLiveData<>();
    private final AtomicBoolean analyzeLock = new AtomicBoolean(false);

    public ScanViewModel() {
        ScanResult initial = ScanAnalyzer.fallbackResult("object");
        uiState.setValue(new ScanUiState(initial, "object", initial.getWord(), false, false, null));
    }

    public LiveData<ScanUiState> getUiState() {
        return uiState;
    }

    public boolean startAnalysis(boolean showLoading) {
        if (!analyzeLock.compareAndSet(false, true)) {
            return false;
        }
        ScanUiState current = getCurrentState();
        uiState.postValue(new ScanUiState(
                current.getScanResult(),
                current.getRawAiLabel(),
                current.getMappedWord(),
                showLoading,
                true,
                null
        ));
        return true;
    }

    public void completeAnalysis(@Nullable ScanResult result,
                                 @Nullable String rawAiLabel,
                                 @Nullable String mappedWord,
                                 @Nullable String message) {
        analyzeLock.set(false);
        ScanUiState current = getCurrentState();
        ScanResult finalResult = result != null ? result : current.getScanResult();
        String finalRawLabel = rawAiLabel != null ? rawAiLabel : current.getRawAiLabel();
        String finalMappedWord = mappedWord != null ? mappedWord : finalResult.getWord();
        uiState.postValue(new ScanUiState(finalResult, finalRawLabel, finalMappedWord, false, false, message));
    }

    public void failAnalysis(@Nullable String message) {
        analyzeLock.set(false);
        ScanUiState current = getCurrentState();
        uiState.postValue(new ScanUiState(
                current.getScanResult(),
                current.getRawAiLabel(),
                current.getMappedWord(),
                false,
                false,
                message
        ));
    }

    public void clearMessage() {
        ScanUiState current = getCurrentState();
        uiState.setValue(new ScanUiState(
                current.getScanResult(),
                current.getRawAiLabel(),
                current.getMappedWord(),
                current.isLoading(),
                current.isAnalyzing(),
                null
        ));
    }

    public ScanResult getCurrentResult() {
        return getCurrentState().getScanResult();
    }

    private ScanUiState getCurrentState() {
        ScanUiState state = uiState.getValue();
        if (state == null) {
            ScanResult fallback = ScanAnalyzer.fallbackResult("object");
            return new ScanUiState(fallback, "object", fallback.getWord(), false, false, null);
        }
        return state;
    }
}
