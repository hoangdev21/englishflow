package com.example.englishflow.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ScanLabelFusion {

    public enum Mode {
        REALTIME,
        CAPTURE,
        GALLERY
    }

    public static final class Candidate {
        private final String label;
        private final float confidence;

        public Candidate(String label, float confidence) {
            this.label = normalize(label);
            this.confidence = confidence;
        }

        public String getLabel() {
            return label;
        }

        public float getConfidence() {
            return confidence;
        }
    }

    private static final Set<String> GENERIC_LABELS = new HashSet<>(Arrays.asList(
            "object", "animal", "plant", "food", "person", "product", "thing"
    ));

    private ScanLabelFusion() {
    }

    public static String chooseBestLabel(Candidate objectCandidate,
                                         Candidate imageCandidate,
                                         Mode mode,
                                         boolean objectSpecific,
                                         boolean imageSpecific) {
        Candidate objectAdjusted = applyThreshold(objectCandidate, getObjectThreshold(mode));
        Candidate imageAdjusted = applyThreshold(imageCandidate, getImageThreshold(mode));

        String objectLabel = objectAdjusted.getLabel();
        String imageLabel = imageAdjusted.getLabel();

        if (imageSpecific && !objectSpecific) {
            return imageLabel;
        }
        if (objectSpecific && !imageSpecific) {
            return objectLabel;
        }

        if (isGeneric(objectLabel) && !isGeneric(imageLabel)) {
            return imageLabel;
        }
        if (!isGeneric(objectLabel) && isGeneric(imageLabel)) {
            return objectLabel;
        }

        if (imageAdjusted.getConfidence() > objectAdjusted.getConfidence() && !"object".equals(imageLabel)) {
            return imageLabel;
        }
        if (!"object".equals(objectLabel)) {
            return objectLabel;
        }
        return imageLabel;
    }

    public static float getImageThreshold(Mode mode) {
        switch (mode) {
            case REALTIME:
                return 0.70f;
            case CAPTURE:
                return 0.55f;
            case GALLERY:
                return 0.50f;
            default:
                return 0.60f;
        }
    }

    public static float getObjectThreshold(Mode mode) {
        switch (mode) {
            case REALTIME:
                return 0.65f;
            case CAPTURE:
                return 0.50f;
            case GALLERY:
                return 0.45f;
            default:
                return 0.55f;
        }
    }

    private static Candidate applyThreshold(Candidate candidate, float threshold) {
        if (candidate == null || candidate.getConfidence() < threshold) {
            return new Candidate("object", 0f);
        }
        return candidate;
    }

    public static String normalize(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "object";
        }
        return label.trim().toLowerCase(Locale.US);
    }

    private static boolean isGeneric(String label) {
        return GENERIC_LABELS.contains(normalize(label));
    }
}
