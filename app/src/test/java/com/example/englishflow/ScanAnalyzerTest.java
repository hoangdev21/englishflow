package com.example.englishflow;

import static org.junit.Assert.assertEquals;

import com.example.englishflow.data.ScanAnalyzer;
import com.example.englishflow.data.ScanResult;

import org.junit.Test;

public class ScanAnalyzerTest {

    @Test
    public void extractPrimaryWord_returnsFirstValidToken() {
        String result = ScanAnalyzer.extractPrimaryWord("BOOK 2026");
        assertEquals("book", result);
    }

    @Test
    public void fromDetectedText_mapsKnownWord() {
        ScanResult result = ScanAnalyzer.fromDetectedText("book");
        assertEquals("book", result.getWord());
        assertEquals("Học tập", result.getCategory());
    }

    @Test
    public void fromDetectedText_fallbackWhenEmptyText() {
        ScanResult result = ScanAnalyzer.fromDetectedText("");
        assertEquals("object", result.getWord());
        assertEquals("Tổng quát", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_mapsObjectLabel() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("Chair");
        assertEquals("chair", result.getWord());
        assertEquals("Nội thất", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_mapsPhone() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("Phone");
        assertEquals("phone", result.getWord());
        assertEquals("Công nghệ", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_fallbackWhenEmpty() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("");
        assertEquals("object", result.getWord());
    }

    @Test
    public void fromDetectedLabel_fallbackWhenNull() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel(null);
        assertEquals("object", result.getWord());
    }

    @Test
    public void fromDetectedLabel_mapsCellPhoneAliasToPhone() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("cell phone");
        assertEquals("phone", result.getWord());
        assertEquals("Công nghệ", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_mapsDeskAliasToTable() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("desk");
        assertEquals("table", result.getWord());
        assertEquals("Nội thất", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_mapsMonitorAliasToTelevision() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("monitor");
        assertEquals("television", result.getWord());
        assertEquals("Cong nghe", result.getCategory());
    }

    @Test
    public void fromDetectedLabel_mapsRoomCorrectly() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("room");
        assertEquals("room", result.getWord());
        assertEquals("phong", result.getMeaning());
    }

    @Test
    public void fromDetectedLabel_unknownWordDoesNotPretendObjectMeaning() {
        ScanResult result = ScanAnalyzer.fromDetectedLabel("ceiling");
        assertEquals("ceiling", result.getWord());
        assertEquals("chua co trong tu dien", result.getMeaning());
        assertEquals("Can bo sung", result.getCategory());
    }
}
