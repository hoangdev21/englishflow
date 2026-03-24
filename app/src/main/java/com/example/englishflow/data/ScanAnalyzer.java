package com.example.englishflow.data;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ScanAnalyzer {

    private ScanAnalyzer() {
    }

    public static ScanResult fromDetectedText(String rawText) {
        String primaryWord = extractPrimaryWord(rawText);
        return fromWord(primaryWord);
    }

    public static ScanResult fromDetectedLabel(String objectLabel) {
        if (objectLabel == null || objectLabel.trim().isEmpty()) {
            return fallbackResult("object");
        }
        return fromWord(canonicalizeLabel(objectLabel));
    }

    public static ScanResult fallbackResult(String word) {
        return fromWord(word);
    }

    public static boolean hasSpecificMapping(String label) {
        if (label == null || label.trim().isEmpty()) {
            return false;
        }

        String normalized = canonicalizeLabel(label);
        switch (normalized) {
            case "bottle":
            case "book":
            case "phone":
            case "laptop":
            case "cup":
            case "chair":
            case "table":
            case "room":
            case "television":
            case "person":
            case "dog":
            case "cat":
                return true;
            default:
                return false;
        }
    }

    public static String toVietnameseLabel(String label) {
        String normalized = canonicalizeLabel(label);
        switch (normalized) {
            case "bottle":
                return "chai";
            case "book":
                return "sach";
            case "phone":
                return "dien thoai";
            case "laptop":
                return "laptop";
            case "cup":
                return "coc";
            case "chair":
                return "ghe";
            case "table":
                return "ban";
            case "room":
                return "phong";
            case "television":
                return "tivi";
            case "person":
                return "nguoi";
            case "dog":
                return "cho";
            case "cat":
                return "meo";
            case "sofa":
                return "ghe sofa";
            case "bed":
                return "giuong";
            case "door":
                return "cua";
            case "window":
                return "cua so";
            case "clock":
                return "dong ho";
            case "keyboard":
                return "ban phim";
            case "mouse":
                return "chuot may tinh";
            case "car":
                return "xe hoi";
            case "bicycle":
                return "xe dap";
            default:
                return "vat the";
        }
    }

    public static String canonicalizeLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "object";
        }

        String normalized = label.toLowerCase(Locale.US).trim();
        switch (normalized) {
            case "cell phone":
            case "mobile phone":
            case "smartphone":
                return "phone";
            case "notebook":
            case "textbook":
            case "novel":
                return "book";
            case "mug":
            case "coffee cup":
            case "teacup":
                return "cup";
            case "glass":
            case "wine glass":
                return "bottle";
            case "armchair":
            case "office chair":
                return "chair";
            case "dining table":
            case "coffee table":
                return "table";
            case "desk":
                return "table";
            case "computer":
            case "notebook computer":
            case "pc":
                return "laptop";
            case "tv":
            case "monitor":
            case "display":
                return "television";
            case "human":
            case "man":
            case "woman":
                return "person";
            case "living room":
            case "bedroom":
            case "classroom":
            case "meeting room":
                return "room";
            case "puppy":
                return "dog";
            case "kitten":
                return "cat";
            case "couch":
                return "sofa";
            case "bed frame":
                return "bed";
            case "entry door":
                return "door";
            case "wall clock":
                return "clock";
            case "computer keyboard":
                return "keyboard";
            case "computer mouse":
                return "mouse";
            case "automobile":
                return "car";
            case "bike":
                return "bicycle";
            case "motorbike":
                return "motorcycle";
            default:
                return normalized;
        }
    }

    public static String extractPrimaryWord(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return "object";
        }

        String[] tokens = rawText.toLowerCase(Locale.US).split("[^a-zA-Z]+");
        for (String token : tokens) {
            if (token.length() >= 3) {
                return token;
            }
        }
        return "object";
    }

    private static ScanResult fromWord(String sourceWord) {
        String normalized = canonicalizeLabel(sourceWord);

        switch (normalized) {
            case "bottle":
                return create(
                        "bottle",
                        "/ˈbɒt.əl/",
                        "chai, lọ",
                        "Please recycle this plastic bottle.",
                        "Nhà cửa",
                        "The word 'bottle' comes from Medieval Latin 'butticula'.",
                        Arrays.asList("glass", "container", "liquid")
                );
            case "book":
                return create(
                        "book",
                        "/bʊk/",
                        "sách",
                        "I read a new English book every month.",
                        "Học tập",
                        "A good book can improve your vocabulary quickly.",
                        Arrays.asList("page", "library", "reading")
                );
            case "phone":
                return create(
                        "phone",
                        "/fəʊn/",
                        "điện thoại",
                        "I use my phone to practice listening every day.",
                        "Công nghệ",
                        "You can switch your phone language to English for daily practice.",
                        Arrays.asList("call", "screen", "message")
                );
            case "laptop":
                return create(
                        "laptop",
                        "/ˈlæp.tɒp/",
                        "máy tính xách tay",
                        "She studies online with her laptop.",
                        "Công nghệ",
                        "Laptop combines 'lap' and 'top', meaning a computer for your lap.",
                        Arrays.asList("keyboard", "screen", "computer")
                );
            case "cup":
                return create(
                        "cup",
                        "/kʌp/",
                        "cái cốc",
                        "Would you like a cup of tea?",
                        "Đồ dùng",
                        "Cup is one of the most common words in daily English conversations.",
                        Arrays.asList("mug", "drink", "tea")
                );
            case "chair":
                return create(
                        "chair",
                        "/tʃeər/",
                        "cái ghế",
                        "Please sit on this chair.",
                        "Nội thất",
                        "The silent letters in English are easier when you learn by object.",
                        Arrays.asList("seat", "table", "furniture")
                );
            case "table":
                return create(
                        "table",
                        "/ˈteɪ.bəl/",
                        "cái bàn",
                        "The notebook is on the table.",
                        "Nội thất",
                        "Many beginners first learn 'table' and 'chair' as a pair.",
                        Arrays.asList("desk", "furniture", "surface")
                );
                    case "object":
                    return create(
                        "object",
                        "/ˈɒb.dʒɪkt/",
                        "vật thể",
                        "This object appears in your photo.",
                        "Tổng quát",
                        "Try taking a clearer photo or include printed text for better recognition.",
                        Arrays.asList("item", "thing", "shape")
                    );
                    case "room":
                    return create(
                        "room",
                        "/ruːm/",
                        "phong",
                        "This room is very quiet for studying English.",
                        "Noi that",
                        "You can learn many daily words by describing objects in your room.",
                        Arrays.asList("house", "bedroom", "living room")
                    );
                    case "television":
                    return create(
                        "television",
                        "/ˈtel.ɪ.vɪʒ.ən/",
                        "tivi",
                        "We watched an English movie on television.",
                        "Cong nghe",
                        "Try watching cartoons with English subtitles for better listening.",
                        Arrays.asList("tv", "screen", "remote")
                    );
                    case "person":
                    return create(
                        "person",
                        "/ˈpɜː.sən/",
                        "nguoi",
                        "That person is speaking English clearly.",
                        "Tong quat",
                        "Use 'person' for singular and 'people' for plural.",
                        Arrays.asList("people", "human", "friend")
                    );
                    case "dog":
                    return create(
                        "dog",
                        "/dɒɡ/",
                        "cho",
                        "The dog is running in the yard.",
                        "Dong vat",
                        "Dogs are common vocabulary words for beginners.",
                        Arrays.asList("pet", "animal", "puppy")
                    );
                    case "cat":
                    return create(
                        "cat",
                        "/kæt/",
                        "meo",
                        "The cat is sleeping on the chair.",
                        "Dong vat",
                        "Cats and dogs are useful starter vocabulary.",
                        Arrays.asList("pet", "animal", "kitten")
                    );
                    case "sofa":
                    return create(
                        "sofa",
                        "/ˈsəʊ.fə/",
                        "ghe sofa",
                        "The sofa is near the window.",
                        "Noi that",
                        "Sofa and couch are often used with the same meaning.",
                        Arrays.asList("couch", "living room", "seat")
                    );
                    case "bed":
                    return create(
                        "bed",
                        "/bed/",
                        "giuong",
                        "I make my bed every morning.",
                        "Noi that",
                        "Bedroom vocabulary helps in daily conversation.",
                        Arrays.asList("pillow", "blanket", "bedroom")
                    );
                    case "door":
                    return create(
                        "door",
                        "/dɔːr/",
                        "cua",
                        "Please close the door quietly.",
                        "Nha cua",
                        "Door is one of the first home words for beginners.",
                        Arrays.asList("window", "room", "handle")
                    );
                    case "window":
                    return create(
                        "window",
                        "/ˈwɪn.dəʊ/",
                        "cua so",
                        "Open the window for fresh air.",
                        "Nha cua",
                        "Window pairs naturally with curtain and glass.",
                        Arrays.asList("glass", "curtain", "light")
                    );
                    case "clock":
                    return create(
                        "clock",
                        "/klɒk/",
                        "dong ho",
                        "The clock on the wall is slow.",
                        "Do dung",
                        "Clock and watch are different in English.",
                        Arrays.asList("time", "watch", "wall")
                    );
                    case "keyboard":
                    return create(
                        "keyboard",
                        "/ˈkiː.bɔːd/",
                        "ban phim",
                        "I type quickly on my keyboard.",
                        "Cong nghe",
                        "Practice typing in English to improve spelling.",
                        Arrays.asList("computer", "mouse", "keys")
                    );
                    case "mouse":
                    return create(
                        "mouse",
                        "/maʊs/",
                        "chuot may tinh",
                        "Click the icon with the mouse.",
                        "Cong nghe",
                        "A computer mouse is different from the animal mouse.",
                        Arrays.asList("keyboard", "click", "cursor")
                    );
                    case "car":
                    return create(
                        "car",
                        "/kɑːr/",
                        "xe hoi",
                        "Their car is parked outside.",
                        "Giao thong",
                        "Car vocabulary is common in travel and daily life.",
                        Arrays.asList("road", "driver", "wheel")
                    );
                    case "bicycle":
                    return create(
                        "bicycle",
                        "/ˈbaɪ.sɪ.kəl/",
                        "xe dap",
                        "He rides a bicycle to school.",
                        "Giao thong",
                        "Bike is the short and common form of bicycle.",
                        Arrays.asList("bike", "helmet", "pedal")
                    );
            default:
                return create(
                        normalized.isEmpty() ? "object" : normalized,
                    "-",
                    "chua co trong tu dien",
                    "This label has not been mapped yet.",
                    "Can bo sung",
                    "Nhan dang dung nhan, nhung chua co du lieu nghia. Ban co the bo sung mapping moi.",
                    Arrays.asList("unknown", "label", "mapping")
                );
        }
    }

    private static ScanResult create(String word, String ipa, String meaning, String example,
                                     String category, String funFact, List<String> relatedWords) {
        return new ScanResult(word, ipa, meaning, example, category, funFact, relatedWords);
    }
}
