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
                        "noun",
                        "Please recycle this plastic bottle.",
                        "Làm ơn hãy tái chế chiếc chai nhựa này.",
                        "Nhà cửa",
                        "The word 'bottle' comes from Medieval Latin 'butticula'.",
                        Arrays.asList("glass", "container", "liquid")
                );
            case "book":
                return create(
                        "book",
                        "/bʊk/",
                        "sách",
                        "noun",
                        "I read a new English book every month.",
                        "Tôi đọc một cuốn sách tiếng Anh mới mỗi tháng.",
                        "Học tập",
                        "A good book can improve your vocabulary quickly.",
                        Arrays.asList("page", "library", "reading")
                );
            case "phone":
                return create(
                        "phone",
                        "/fəʊn/",
                        "điện thoại",
                        "noun",
                        "I use my phone to practice listening every day.",
                        "Tôi sử dụng điện thoại để luyện nghe mỗi ngày.",
                        "Công nghệ",
                        "You can switch your phone language to English for daily practice.",
                        Arrays.asList("call", "screen", "message")
                );
            case "laptop":
                return create(
                        "laptop",
                        "/ˈlæp.tɒp/",
                        "máy tính xách tay",
                        "noun",
                        "She studies online with her laptop.",
                        "Cô ấy học trực tuyến với chiếc máy tính xách tay của mình.",
                        "Công nghệ",
                        "Laptop combines 'lap' and 'top', meaning a computer for your lap.",
                        Arrays.asList("keyboard", "screen", "computer")
                );
            case "cup":
                return create(
                        "cup",
                        "/kʌp/",
                        "cái cốc",
                        "noun",
                        "Would you like a cup of tea?",
                        "Bạn có muốn một tách trà không?",
                        "Đồ dùng",
                        "Cup is one of the most common words in daily English conversations.",
                        Arrays.asList("mug", "drink", "tea")
                );
            case "chair":
                return create(
                        "chair",
                        "/tʃeər/",
                        "cái ghế",
                        "noun",
                        "Please sit on this chair.",
                        "Làm ơn hãy ngồi lên chiếc ghế này.",
                        "Nội thất",
                        "The silent letters in English are easier when you learn by object.",
                        Arrays.asList("seat", "table", "furniture")
                );
            case "table":
                return create(
                        "table",
                        "/ˈteɪ.bəl/",
                        "cái bàn",
                        "noun",
                        "The notebook is on the table.",
                        "Cuốn sổ tay ở trên bàn.",
                        "Nội thất",
                        "Many beginners first learn 'table' and 'chair' as a pair.",
                        Arrays.asList("desk", "furniture", "surface")
                );
                    case "object":
                    return create(
                        "object",
                        "/ˈɒb.dʒɪkt/",
                        "vật thể",
                        "noun",
                        "This object appears in your photo.",
                        "Vật thể này xuất hiện trong ảnh của bạn.",
                        "Tổng quát",
                        "Try taking a clearer photo or include printed text for better recognition.",
                        Arrays.asList("item", "thing", "shape")
                    );
                    case "room":
                    return create(
                        "room",
                        "/ruːm/",
                        "phong",
                        "noun",
                        "This room is very quiet for studying English.",
                        "Căn phòng này rất yên tĩnh để học tiếng Anh.",
                        "Nội thất",
                        "You can learn many daily words by describing objects in your room.",
                        Arrays.asList("house", "bedroom", "living room")
                    );
                    case "television":
                    return create(
                        "television",
                        "/ˈtel.ɪ.vɪʒ.ən/",
                        "tivi",
                        "noun",
                        "We watched an English movie on television.",
                        "Chúng tôi đã xem một bộ phim tiếng Anh trên tivi.",
                        "Công nghệ",
                        "Try watching cartoons with English subtitles for better listening.",
                        Arrays.asList("tv", "screen", "remote")
                    );
                    case "person":
                    return create(
                        "person",
                        "/ˈpɜː.sən/",
                        "nguời",
                        "noun",
                        "That person is speaking English clearly.",
                        "Người đó đang nói tiếng Anh rất rõ ràng.",
                        "Tổng quát",
                        "Use 'person' for singular and 'people' for plural.",
                        Arrays.asList("people", "human", "friend")
                    );
                    case "dog":
                    return create(
                        "dog",
                        "/dɒɡ/",
                        "chó",
                        "noun",
                        "The dog is running in the yard.",
                        "Con chó đang chạy trong sân.",
                        "Động vật",
                        "Dogs are common vocabulary words for beginners.",
                        Arrays.asList("pet", "animal", "puppy")
                    );
                    case "cat":
                    return create(
                        "cat",
                        "/kæt/",
                        "mèo",
                        "noun",
                        "The cat is sleeping on the chair.",
                        "Con mèo đang ngủ trên ghế.",
                        "Động vật",
                        "Cats and dogs are useful starter vocabulary.",
                        Arrays.asList("pet", "animal", "kitten")
                    );
                    case "sofa":
                    return create(
                        "sofa",
                        "/ˈsəʊ.fə/",
                        "ghế sofa",
                        "noun",
                        "The sofa is near the window.",
                        "Cái ghế sofa ở gần cửa sổ.",
                        "Nội thất",
                        "Sofa and couch are often used with the same meaning.",
                        Arrays.asList("couch", "living room", "seat")
                    );
                    case "bed":
                    return create(
                        "bed",
                        "/bed/",
                        "giường",
                        "noun",
                        "I make my bed every morning.",
                        "Tôi dọn giường mỗi sáng.",
                        "Nội thất",
                        "Bedroom vocabulary helps in daily conversation.",
                        Arrays.asList("pillow", "blanket", "bedroom")
                    );
                    case "door":
                    return create(
                        "door",
                        "/dɔːr/",
                        "cửa",
                        "noun",
                        "Please close the door quietly.",
                        "Làm ơn đóng cửa lại một cách nhẹ nhàng.",
                        "Nhà cửa",
                        "Door is one of the first home words for beginners.",
                        Arrays.asList("window", "room", "handle")
                    );
                    case "window":
                    return create(
                        "window",
                        "/ˈwɪn.dəʊ/",
                        "cửa sổ",
                        "noun",
                        "Open the window for fresh air.",
                        "Mở cửa sổ ra để đón không khí trong lành.",
                        "Nhà cửa",
                        "Window pairs naturally with curtain and glass.",
                        Arrays.asList("glass", "curtain", "light")
                    );
                    case "clock":
                    return create(
                        "clock",
                        "/klɒk/",
                        "đồng hồ",
                        "noun",
                        "The clock on the wall is slow.",
                        "Cái đồng hồ trên tường bị chậm.",
                        "Đồ dùng",
                        "Clock and watch are different in English.",
                        Arrays.asList("time", "watch", "wall")
                    );
                    case "keyboard":
                    return create(
                        "keyboard",
                        "/ˈkiː.bɔːd/",
                        "bàn phím",
                        "noun",
                        "I type quickly on my keyboard.",
                        "Tôi gõ rất nhanh trên bàn phím của mình.",
                        "Công nghệ",
                        "Practice typing in English to improve spelling.",
                        Arrays.asList("computer", "mouse", "keys")
                    );
                    case "mouse":
                    return create(
                        "mouse",
                        "/maʊs/",
                        "chuột máy tính",
                        "noun",
                        "Click the icon with the mouse.",
                        "Nhấp vào biểu tượng bằng chuột.",
                        "Công nghệ",
                        "A computer mouse is different from the animal mouse.",
                        Arrays.asList("keyboard", "click", "cursor")
                    );
                    case "car":
                    return create(
                        "car",
                        "/kɑːr/",
                        "xe hơi",
                        "noun",
                        "Their car is parked outside.",
                        "Xe của họ đang đỗ bên ngoài.",
                        "Giao thông",
                        "Car vocabulary is common in travel and daily life.",
                        Arrays.asList("road", "driver", "wheel")
                    );
                    case "bicycle":
                    return create(
                        "bicycle",
                        "/ˈbaɪ.sɪ.kəl/",
                        "xe đạp",
                        "noun",
                        "He rides a bicycle to school.",
                        "Anh ấy đạp xe đi học.",
                        "Giao thông",
                        "Bike is the short and common form of bicycle.",
                        Arrays.asList("bike", "helmet", "pedal")
                    );
            default:
                return create(
                        normalized.isEmpty() ? "object" : normalized,
                    "-",
                    "chưa có trong từ điển",
                    "noun",
                    "This label has not been mapped yet.",
                    "Nhãn này chưa được ánh xạ.",
                    "Cần bổ sung",
                    "Nhận dạng đúng nhãn, nhưng chưa có dữ liệu nghĩa. Bạn có thể bổ sung mapping mới.",
                    Arrays.asList("unknown", "label", "mapping")
                );
        }
    }

    private static ScanResult create(String word, String ipa, String meaning, String wordType, String example,
                                     String exampleVi, String category, String funFact, List<String> relatedWords) {
        return new ScanResult(word, ipa, meaning, wordType, example, exampleVi, category, funFact, relatedWords);
    }
}
