package com.example.englishflow;

import static org.junit.Assert.assertEquals;

import com.example.englishflow.data.ScanLabelFusion;
import com.example.englishflow.data.ScanLabelFusion.Candidate;
import com.example.englishflow.data.ScanLabelFusion.Mode;

import org.junit.Test;

public class ScanLabelFusionTest {

    @Test
    public void chooseBestLabel_realtimeRejectsLowConfidenceImageLabel() {
        Candidate objectCandidate = new Candidate("object", 0.90f);
        Candidate imageCandidate = new Candidate("book", 0.65f);

        String result = ScanLabelFusion.chooseBestLabel(
                objectCandidate,
                imageCandidate,
                Mode.REALTIME,
                false,
                true
        );

        assertEquals("object", result);
    }

    @Test
    public void chooseBestLabel_galleryAcceptsSameImageLabelWithLowerThreshold() {
        Candidate objectCandidate = new Candidate("object", 0.90f);
        Candidate imageCandidate = new Candidate("book", 0.65f);

        String result = ScanLabelFusion.chooseBestLabel(
                objectCandidate,
                imageCandidate,
                Mode.GALLERY,
                false,
                true
        );

        assertEquals("book", result);
    }

    @Test
    public void chooseBestLabel_prefersSpecificMappedLabel() {
        Candidate objectCandidate = new Candidate("food", 0.95f);
        Candidate imageCandidate = new Candidate("chair", 0.80f);

        String result = ScanLabelFusion.chooseBestLabel(
                objectCandidate,
                imageCandidate,
                Mode.CAPTURE,
                false,
                true
        );

        assertEquals("chair", result);
    }

    @Test
    public void chooseBestLabel_prefersObjectWhenImageIsGeneric() {
        Candidate objectCandidate = new Candidate("laptop", 0.75f);
        Candidate imageCandidate = new Candidate("object", 0.92f);

        String result = ScanLabelFusion.chooseBestLabel(
                objectCandidate,
                imageCandidate,
                Mode.CAPTURE,
                true,
                false
        );

        assertEquals("laptop", result);
    }

    @Test
    public void normalize_returnsObjectForEmptyInput() {
        assertEquals("object", ScanLabelFusion.normalize("   "));
    }
}
