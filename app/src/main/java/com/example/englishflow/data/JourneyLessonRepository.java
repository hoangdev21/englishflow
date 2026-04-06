package com.example.englishflow.data;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JourneyLessonRepository {

    public static final int DEFAULT_JOURNEY_SIZE = 10;
    private static final String COLLECTION_MAP_LESSONS = "map_lessons";
    private static final long LESSON_CACHE_TTL_MS = 3L * 60L * 1000L;
    private static final ExecutorService PARSE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Object LESSON_CACHE_LOCK = new Object();
    private static List<MapNodeItem> cachedLessons;
    private static long cachedLessonsAtMs;
    private static boolean refreshInFlight;
    private static final List<LessonCallback> pendingCallbacks = new ArrayList<>();

    private final FirebaseFirestore firestore;
    private final Handler mainHandler;

    public interface LessonCallback {
        void onResult(List<MapNodeItem> lessons);
    }

    public JourneyLessonRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void fetchLessons(LessonCallback callback) {
        if (callback == null) {
            return;
        }

        long now = System.currentTimeMillis();
        List<MapNodeItem> hotCache = getCachedLessonsSnapshot();
        boolean cacheFresh = hotCache != null && !hotCache.isEmpty() && (now - getCachedAtMs()) <= LESSON_CACHE_TTL_MS;

        if (hotCache != null && !hotCache.isEmpty()) {
            deliver(callback, hotCache);
            if (cacheFresh) {
                return;
            }
        }

        boolean queueCallback = hotCache == null || hotCache.isEmpty();
        synchronized (LESSON_CACHE_LOCK) {
            if (queueCallback) {
                pendingCallbacks.add(callback);
            }
            if (refreshInFlight) {
                return;
            }
            refreshInFlight = true;
        }

        if (queueCallback) {
            fetchFromFirestoreCacheThenServer();
        } else {
            fetchFromFirestoreServer();
        }
    }

    private void deliver(LessonCallback callback, List<MapNodeItem> lessons) {
        List<MapNodeItem> safeLessons = copyLessons(lessons);
        mainHandler.post(() -> callback.onResult(safeLessons));
    }

    private void fetchFromFirestoreCacheThenServer() {
        firestore.collection(COLLECTION_MAP_LESSONS)
                .get(Source.CACHE)
                .addOnSuccessListener(snapshot -> PARSE_EXECUTOR.execute(() -> {
                    List<MapNodeItem> parsed = parseSnapshot(snapshot != null
                            ? snapshot.getDocuments()
                            : Collections.emptyList());
                    if (!parsed.isEmpty()) {
                        setCachedLessons(parsed);
                        finishRefresh(parsed);
                        return;
                    }
                    fetchFromFirestoreServer();
                }))
                .addOnFailureListener(error -> fetchFromFirestoreServer());
    }

    private void fetchFromFirestoreServer() {
        firestore.collection(COLLECTION_MAP_LESSONS)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> PARSE_EXECUTOR.execute(() -> {
                    List<MapNodeItem> parsed = parseSnapshot(snapshot != null
                            ? snapshot.getDocuments()
                            : Collections.emptyList());

                    if (parsed.isEmpty()) {
                        parsed = resolveFallbackLessons();
                    }

                    setCachedLessons(parsed);
                    finishRefresh(parsed);
                }))
                .addOnFailureListener(error -> PARSE_EXECUTOR.execute(() -> {
                    List<MapNodeItem> fallback = resolveFallbackLessons();
                    setCachedLessons(fallback);
                    finishRefresh(fallback);
                }));
    }

    private List<MapNodeItem> resolveFallbackLessons() {
        List<MapNodeItem> cache = getCachedLessonsSnapshot();
        if (cache != null && !cache.isEmpty()) {
            return cache;
        }
        return buildFallbackJourney();
    }

    private void finishRefresh(List<MapNodeItem> lessons) {
        List<LessonCallback> callbacks;
        synchronized (LESSON_CACHE_LOCK) {
            refreshInFlight = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        if (callbacks.isEmpty()) {
            return;
        }

        for (LessonCallback callback : callbacks) {
            deliver(callback, lessons);
        }
    }

    private List<MapNodeItem> getCachedLessonsSnapshot() {
        synchronized (LESSON_CACHE_LOCK) {
            if (cachedLessons == null || cachedLessons.isEmpty()) {
                return null;
            }
            return copyLessons(cachedLessons);
        }
    }

    private long getCachedAtMs() {
        synchronized (LESSON_CACHE_LOCK) {
            return cachedLessonsAtMs;
        }
    }

    private void setCachedLessons(List<MapNodeItem> lessons) {
        synchronized (LESSON_CACHE_LOCK) {
            cachedLessons = copyLessons(lessons);
            cachedLessonsAtMs = System.currentTimeMillis();
        }
    }

    private List<MapNodeItem> copyLessons(List<MapNodeItem> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return new ArrayList<>();
        }

        List<MapNodeItem> copies = new ArrayList<>(lessons.size());
        for (MapNodeItem item : lessons) {
            if (item == null) {
                continue;
            }

            List<LessonVocabulary> copiedVocab = new ArrayList<>();
            for (LessonVocabulary vocab : item.getVocabList()) {
                if (vocab == null) {
                    continue;
                }
                LessonVocabulary copy = new LessonVocabulary(
                        vocab.getWord(),
                        vocab.getIpa(),
                        vocab.getMeaning(),
                        vocab.getExample()
                );
                copy.setPracticed(vocab.isPracticed());
                copiedVocab.add(copy);
            }

            copies.add(new MapNodeItem(
                    item.getNodeId(),
                    item.getTitle(),
                    item.getEmoji(),
                    item.getPromptKey(),
                    item.getRoleDescription(),
                    item.getMinExchanges(),
                    copiedVocab,
                    item.getStatus(),
                    item.getMinLevel(),
                    new ArrayList<>(item.getFlowSteps()),
                    new ArrayList<>(item.getLessonKeywords())
            ));
        }

        return copies;
    }

    private List<MapNodeItem> parseSnapshot(List<com.google.firebase.firestore.DocumentSnapshot> docs) {
        List<LessonCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < docs.size(); i++) {
            com.google.firebase.firestore.DocumentSnapshot doc = docs.get(i);
            if (doc == null) {
                continue;
            }

            String status = safeString(doc.get("status")).toLowerCase(Locale.US);
            if ("draft".equals(status) || "archived".equals(status) || "hidden".equals(status) || "disabled".equals(status)) {
                continue;
            }

            String lessonId = nonEmpty(safeString(doc.get("lesson_id")), doc.getId());
            if (lessonId.isEmpty()) {
                continue;
            }

            String title = nonEmpty(safeString(doc.get("title")), lessonId);
            String emoji = nonEmpty(safeString(doc.get("emoji")), "📘");
            String promptKey = nonEmpty(safeString(doc.get("prompt_key")), lessonId);
            String roleDescription = nonEmpty(
                    safeString(doc.get("role_description")),
                    safeString(doc.get("scenario")),
                    safeString(doc.get("content"))
            );
            String minLevel = nonEmpty(safeString(doc.get("min_level")), "A1");
            int minExchanges = Math.max(2, safeInt(doc.get("min_exchanges")));
            if (minExchanges == 2) {
                minExchanges = 4;
            }

            List<MapLessonFlowStep> flowSteps = parseFlowSteps(doc.get("flow_steps"));
            if (roleDescription.isEmpty()) {
                roleDescription = extractRoleContext(flowSteps);
            }

            List<LessonVocabulary> vocabulary = parseVocabulary(doc.get("vocabulary"));
            if (vocabulary.isEmpty()) {
                vocabulary = vocabularyFromFlow(flowSteps);
            }

            List<String> lessonKeywords = toStringList(doc.get("keywords"));
            if (lessonKeywords.isEmpty()) {
                lessonKeywords = keywordsFromFlow(flowSteps, vocabulary);
            }

            int order = extractSortOrder(doc, lessonId, i);

            MapNodeItem node = new MapNodeItem(
                    lessonId,
                    title,
                    emoji,
                    promptKey,
                    roleDescription,
                    minExchanges,
                    vocabulary,
                    MapNodeItem.Status.AVAILABLE,
                    minLevel,
                    flowSteps,
                    lessonKeywords
            );

            candidates.add(new LessonCandidate(order, node));
        }

        candidates.sort(Comparator.comparingInt(item -> item.order));
        List<MapNodeItem> result = new ArrayList<>();
        for (LessonCandidate item : candidates) {
            result.add(item.node);
        }
        return result;
    }

    private int extractSortOrder(com.google.firebase.firestore.DocumentSnapshot doc, String lessonId, int fallbackIndex) {
        int fromOrder = safeInt(doc.get("order"));
        if (fromOrder > 0) {
            return fromOrder;
        }

        int fromIndex = safeInt(doc.get("index"));
        if (fromIndex > 0) {
            return fromIndex;
        }

        String digits = lessonId.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits);
            } catch (Exception ignored) {
            }
        }

        return fallbackIndex + 1;
    }

    private List<MapLessonFlowStep> parseFlowSteps(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<MapLessonFlowStep> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                continue;
            }

            Map<?, ?> map = (Map<?, ?>) item;
            String type = safeString(map.get("type"));
            if (type.isEmpty()) {
                continue;
            }

            MapLessonFlowStep step = new MapLessonFlowStep(
                    type,
                    safeString(map.get("content")),
                    safeString(map.get("word")),
                    safeString(map.get("ipa")),
                    safeString(map.get("meaning")),
                    safeString(map.get("instruction")),
                    safeString(map.get("question")),
                    safeString(map.get("expected_keyword")),
                    safeString(map.get("hint")),
                    nonEmpty(safeString(map.get("role_context")), safeString(map.get("scenario"))),
                    toStringList(map.get("accepted_answers"))
            );
            result.add(step);
        }
        return result;
    }

    private List<LessonVocabulary> parseVocabulary(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<LessonVocabulary> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            String word = safeString(map.get("word"));
            if (word.isEmpty()) {
                continue;
            }

            result.add(new LessonVocabulary(
                    word,
                    safeString(map.get("ipa")),
                    safeString(map.get("meaning")),
                    safeString(map.get("example"))
            ));
        }
        return result;
    }

    private List<LessonVocabulary> vocabularyFromFlow(List<MapLessonFlowStep> flowSteps) {
        List<LessonVocabulary> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (MapLessonFlowStep step : flowSteps) {
            String type = step.getTypeNormalized();
            if (!"vocabulary".equals(type) && !"vocab".equals(type)) {
                continue;
            }

            String key = step.getWord().toLowerCase(Locale.US);
            if (key.isEmpty() || seen.contains(key)) {
                continue;
            }

            seen.add(key);
            result.add(new LessonVocabulary(
                    step.getWord(),
                    step.getIpa(),
                    step.getMeaning(),
                    step.getContent()
            ));
        }

        return result;
    }

    private List<String> keywordsFromFlow(List<MapLessonFlowStep> flowSteps, List<LessonVocabulary> vocabList) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        for (MapLessonFlowStep step : flowSteps) {
            if (!step.getWord().isEmpty()) {
                keywords.add(step.getWord());
            }
            if (!step.getExpectedKeyword().isEmpty()) {
                for (String key : splitKeywords(step.getExpectedKeyword())) {
                    keywords.add(key);
                }
            }
            for (String accepted : step.getAcceptedAnswers()) {
                keywords.add(accepted);
            }
        }

        for (LessonVocabulary vocab : vocabList) {
            if (vocab != null && vocab.getWord() != null && !vocab.getWord().trim().isEmpty()) {
                keywords.add(vocab.getWord().trim());
            }
        }

        return new ArrayList<>(keywords);
    }

    private List<String> splitKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split("[,|/]");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            values.add(raw.trim());
        }
        return values;
    }

    private String extractRoleContext(List<MapLessonFlowStep> flowSteps) {
        for (MapLessonFlowStep step : flowSteps) {
            if (!step.getRoleContext().isEmpty()) {
                return step.getRoleContext();
            }
        }
        return "You are Flow, a supportive English coach. Keep the conversation practical and natural.";
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int safeInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(safeString(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String nonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String text = safeString(item);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private List<MapNodeItem> buildFallbackJourney() {
        List<MapNodeItem> lessons = new ArrayList<>();

        lessons.add(buildLesson(
                "lesson_01",
            "Chào hỏi và Làm quen",
                "👋",
                "A1",
                "greetings_intro",
                "You are Flow. Introduce yourself and ask the learner's name with warm energy.",
                4,
                Arrays.asList("Hello", "Good morning", "Good evening", "Name"),
                Arrays.asList(
                intro("Chào bạn, mình là Flow. Hôm nay mình và bạn bắt đầu bài đầu tiên nhé."),
                vocab("Good morning", "/gʊd ˈmɔːrnɪŋ/", "Chào buổi sáng", "Nhấn mic và đọc theo: Good morning.", "Nhớ nhấn mạnh âm MOR trong morning."),
                vocab("Good evening", "/gʊd ˈiːvnɪŋ/", "Chào buổi tối", "Đọc theo: Good evening.", "Nhớ chia cụm thành 2 phần: good + evening."),
                quiz("Khi vừa thức dậy và gặp mẹ, bạn nên chào gì?", "good morning", "Từ khóa cần có: good morning", Arrays.asList("good morning", "morning"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_02",
            "Cảm xúc ngày mới",
                "😊",
                "A1",
                "morning_mood",
                "You are a caring friend checking how the learner feels this morning.",
                4,
                Arrays.asList("Happy", "Sad", "Tired", "Excited", "Hungry"),
                Arrays.asList(
                intro("Buổi sáng nay bạn thấy thế nào? Mình sẽ giúp bạn mô tả cảm xúc bằng tiếng Anh."),
                vocab("Happy", "/ˈhæpi/", "Vui", "Hãy đọc: I feel happy.", "Âm cuối là pi, không đọc thành pe."),
                vocab("Tired", "/ˈtaɪərd/", "Mệt", "Hãy đọc: I am tired.", "Kéo dài âm tai trong tired."),
                quiz("Nếu hôm nay bạn rất vui, bạn sẽ nói câu nào?", "i feel happy", "Từ khóa cần có happy", Arrays.asList("i feel happy", "happy"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_03",
            "Gia đình yêu thương",
                "👨‍👩‍👧",
                "A1",
                "family_talk",
                "You are chatting about family and home routines.",
                4,
                Arrays.asList("Father", "Mother", "Sister", "Brother", "Cook"),
                Arrays.asList(
                intro("Chúng ta nói về gia đình nhé. Bạn hãy giới thiệu một thành viên mà bạn yêu quý."),
                vocab("Mother", "/ˈmʌðər/", "Mẹ", "Đọc theo: My mother is kind.", "Chữ âm th cần đặt lưỡi giữa răng."),
                vocab("Cook", "/kʊk/", "Nấu ăn", "Đọc theo: My father can cook.", "Âm cuối là k ngắn gọn."),
                quiz("Ai là người nấu ăn giỏi nhất trong nhà bạn? Trả lời có từ cook.", "cook", "Dùng từ cook trong câu trả lời", Arrays.asList("cook", "my mother cooks", "my father cooks"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_04",
            "Bữa sáng ngon miệng",
                "☕",
                "A1",
                "breakfast_order",
                "You are a cafe waiter taking a breakfast order politely.",
                5,
                Arrays.asList("Coffee", "Tea", "Bread", "Breakfast", "Delicious"),
                Arrays.asList(
                intro("Bây giờ mình đóng vai phục vụ bữa sáng. Bạn sẵn sàng gọi món chưa?"),
                vocab("Coffee", "/ˈkɔːfi/", "Cà phê", "Đọc theo: I would like coffee.", "Âm co trong coffee tròn môi."),
                vocab("Breakfast", "/ˈbrekfəst/", "Bữa sáng", "Đọc theo: Breakfast is delicious.", "Âm đầu là brek, không đọc nhanh quá."),
                quiz("Nếu bạn muốn gọi một ly trà, bạn nói gì?", "tea", "Trả lời cần có từ tea", Arrays.asList("tea", "i would like tea"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_05",
            "Công việc và Ước mơ",
                "💼",
                "A1",
                "job_and_dream",
                "You are a mentor asking about work and future dreams.",
                5,
                Arrays.asList("Teacher", "Doctor", "Student", "Dream", "Work"),
                Arrays.asList(
                intro("Mình muốn nghe về công việc và ước mơ của bạn."),
                vocab("Work", "/wɜːrk/", "Làm việc", "Đọc theo: I work at a company.", "Âm er trong work cần rõ."),
                vocab("Dream", "/driːm/", "Ước mơ", "Đọc theo: My dream is to be a teacher.", "Kéo dài âm ii trong dream."),
                quiz("Nếu bạn muốn trở thành bác sĩ, bạn nói câu nào?", "doctor", "Dùng từ doctor trong câu", Arrays.asList("doctor", "my dream is to be a doctor"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_06",
            "Thời trang và Màu sắc",
                "👕",
                "A1",
                "fashion_colors",
                "You are a stylist helping the learner describe today's outfit.",
                5,
                Arrays.asList("Blue", "Red", "Shirt", "Wear", "Favorite"),
                Arrays.asList(
                intro("Hôm nay bạn mặc gì? Mình sẽ giúp bạn mô tả trang phục bằng tiếng Anh."),
                vocab("Shirt", "/ʃɜːrt/", "Áo sơ mi", "Đọc theo: I wear a blue shirt.", "Âm sh trong shirt phát âm nhẹ."),
                vocab("Favorite", "/ˈfeɪvərɪt/", "Yêu thích", "Đọc theo: Blue is my favorite color.", "Nhấn âm đầu: FAY-vuh-rit."),
                quiz("Nếu màu yêu thích của bạn là đỏ, bạn nói gì?", "red", "Dùng từ red trong câu", Arrays.asList("red", "my favorite color is red"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_07",
            "Thời tiết hôm nay",
                "🌤️",
                "A1",
                "weather_today",
                "You are a weather reporter speaking clearly and briefly.",
                5,
                Arrays.asList("Sunny", "Rainy", "Cold", "Hot", "Weather"),
                Arrays.asList(
                intro("Hãy cùng làm bản tin thời tiết mini trong 1 phút."),
                vocab("Weather", "/ˈweðər/", "Thời tiết", "Đọc theo: The weather is sunny.", "Âm th giống trong mother."),
                vocab("Rainy", "/ˈreɪni/", "Mưa", "Đọc theo: It is rainy today.", "Âm ray trong rainy cần rõ."),
                quiz("Nếu ngoài trời nắng đẹp, bạn sẽ nói gì?", "sunny", "Từ khóa: sunny", Arrays.asList("sunny", "it is sunny"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_08",
            "Ngôi nhà ấm cúng",
                "🏠",
                "A1",
                "home_space",
                "You are helping the learner describe objects in a room.",
                5,
                Arrays.asList("Bed", "Chair", "Table", "Room", "Home"),
                Arrays.asList(
                intro("Chúng ta sẽ mô tả căn phòng của bạn bằng tiếng Anh."),
                vocab("Chair", "/tʃer/", "Ghế", "Đọc theo: I sit on the chair.", "Âm ch trong chair cần bật hơi."),
                vocab("Bed", "/bed/", "Giường", "Đọc theo: I sleep on the bed.", "Âm e ngắn trong bed."),
                quiz("Nếu bạn đang nằm trên giường, bạn nói câu nào?", "bed", "Dùng từ bed trong câu", Arrays.asList("bed", "i am on the bed", "i sleep on the bed"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_09",
            "Sở thích cuối tuần",
                "🎬",
                "A1",
                "weekend_hobby",
                "You are inviting the learner to weekend activities.",
                5,
                Arrays.asList("Football", "Movie", "Music", "Free time", "Play"),
                Arrays.asList(
                intro("Cuối tuần rồi! Mình rủ bạn đi chơi và nói về sở thích nhé."),
                vocab("Football", "/ˈfʊtbɔːl/", "Bóng đá", "Đọc theo: I play football on weekends.", "Âm foot ngắn, bóng gọn."),
                vocab("Movie", "/ˈmuːvi/", "Phim", "Đọc theo: I watch a movie in my free time.", "Âm moo trong movie cần dài."),
                quiz("Nếu bạn thích nghe nhạc lúc rảnh, bạn nói gì?", "music", "Từ khóa: music", Arrays.asList("music", "i like music", "i listen to music"))
                )
        ));

        lessons.add(buildLesson(
                "lesson_10",
            "Hẹn gặp lại",
                "👋",
                "A1",
                "farewell",
                "You summarize the journey, praise effort, and close warmly.",
                4,
                Arrays.asList("Goodbye", "See you", "Awesome", "Journey"),
                Arrays.asList(
                intro("Bạn đã đi hết hành trình đầu tiên. Giờ mình tổng kết và hẹn gặp lại."),
                vocab("Goodbye", "/ˌɡʊdˈbaɪ/", "Tạm biệt", "Đọc theo: Goodbye and see you soon.", "Nhấn âm bai ở cuối từ."),
                vocab("Journey", "/ˈdʒɜːrni/", "Hành trình", "Đọc theo: This journey is awesome.", "Âm dʒ trong journey rõ nét."),
                quiz("Nếu muốn tạm biệt lịch sự, bạn sẽ nói gì?", "goodbye", "Từ khóa cần có goodbye", Arrays.asList("goodbye", "goodbye see you"))
                )
        ));

        return lessons;
    }

    private MapNodeItem buildLesson(String lessonId,
                                    String title,
                                    String emoji,
                                    String minLevel,
                                    String promptKey,
                                    String roleDescription,
                                    int minExchanges,
                                    List<String> keywords,
                                    List<MapLessonFlowStep> steps) {
        List<LessonVocabulary> vocab = vocabularyFromFlow(steps);
        return new MapNodeItem(
                lessonId,
                title,
                emoji,
                promptKey,
                roleDescription,
                minExchanges,
                vocab,
                MapNodeItem.Status.AVAILABLE,
                minLevel,
                steps,
                keywords
        );
    }

    private MapLessonFlowStep intro(String content) {
        return new MapLessonFlowStep(
                "intro",
                content,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                Collections.emptyList()
        );
    }

    private MapLessonFlowStep vocab(String word, String ipa, String meaning, String instruction, String hint) {
        return new MapLessonFlowStep(
                "vocabulary",
                "",
                word,
                ipa,
                meaning,
                instruction,
                "",
                "",
                hint,
                "",
                Collections.emptyList()
        );
    }

    private MapLessonFlowStep quiz(String question, String expectedKeyword, String hint, List<String> accepted) {
        return new MapLessonFlowStep(
                "situational_quiz",
                "",
                "",
                "",
                "",
                "",
                question,
                expectedKeyword,
                hint,
                "",
                accepted
        );
    }

    private static class LessonCandidate {
        final int order;
        final MapNodeItem node;

        LessonCandidate(int order, MapNodeItem node) {
            this.order = order;
            this.node = node;
        }
    }
}
