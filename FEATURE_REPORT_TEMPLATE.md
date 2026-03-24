# 📊 BÁO CÁO PHÂN TÍCH CHỨC NĂNG SCAN - EnglishFlow App

---

## I. GIỚI THIỆU

### 1.1 Tên Dự Án
**EnglishFlow - Ứng Dụng Học Tiếng Anh Tương Tác**

### 1.2 Chức Năng Đang Phân Tích
**Chức Năng Scan (Quét Từ Vựng)**

### 1.3 Mô Tả Ngắn
Chức năng cho phép người dùng quét hình ảnh chứa từ vựng tiếng Anh để nhận thông tin chi tiết (cách phát âm, nghĩa, ví dụ, từ liên quan).

---

## II. PHÂN TÍCH KIẾN TRÚC

### 2.1 Kiến Trúc Tổng Quát
```
┌─────────────────────────────────────────┐
│       ScanFragment (UI Layer)           │
├─────────────────────────────────────────┤
│    Repository Pattern (Business Logic)  │
├─────────────────────────────────────────┤
│      Room Database (Data Layer)         │
└─────────────────────────────────────────┘
```

### 2.2 Các Component Chính
| Component | Loại | Chức Năng |
|-----------|------|----------|
| **ScanFragment.java** | Fragment | Quản lý UI và tương tác |
| **AppRepository.java** | Repository | Logic xử lý dữ liệu |
| **ScanResult.java** | Model | Lưu kết quả quét |
| **WordEntry.java** | Model | Lưu thông tin từ vựng |
| **LearnedWordEntity** | Entity | Lưu vào Database |
| **UserStatsEntity** | Entity | Lưu thống kê người dùng |

### 2.3 Pattern & Kiến Trúc Sử Dụng
- ✅ **Repository Pattern** - Tách biệt logic dữ liệu từ UI
- ✅ **MVVM** - Model-View-ViewModel separation
- ✅ **Room Database** - ORM cho SQLite
- ✅ **Fragment-based** - Навigation trong ứng dụng

---

## III. CHI TIẾT CHỨC NĂNG

### 3.1 Các Tính Năng Chính

#### 3.1.1 Chọn/Chụp Ảnh
**Trạng thái:** ✅ Chọn từ thư viện (Hoàn tất), ⏳ Chụp ảnh (Demo)

**Công nghệ:**
- ActivityResultLauncher API
- Android Content Provider

**Code:**
```java
// Chọn từ thư viện
pickGalleryButton.setOnClickListener(v -> 
    imagePickerLauncher.launch("image/*")
);

// Xử lý ảnh được chọn
imagePickerLauncher = registerForActivityResult(
    new ActivityResultContracts.GetContent(), 
    this::handleImagePicked
);
```

#### 3.1.2 Phân Tích Ảnh (AI)
**Trạng thái:** ⏳ Demo (Mock data)

**Logic:**
```
User Click "Phân Tích AI"
    ↓
Tăng bộ đếm quét (increaseScanCount)
    ↓
Lấy dữ liệu từ Repository (mockScanResult)
    ↓
Hiển thị kết quả trên UI
```

**Dữ liệu Trả Về:**
- 📝 Từ vựng (word)
- 🔊 Phát âm IPA (ipa)
- 💡 Nghĩa Việt (meaning)
- 💬 Ví dụ câu (example)
- 📂 Danh mục (category)
- 🎓 Sự thật thú vị (funFact)
- 🔗 Từ liên quan (relatedWords - 3 từ)

#### 3.1.3 Phát Âm (TextToSpeech)
**Trạng thái:** ✅ Hoàn tất

**Công nghệ:** Android TextToSpeech API

**Code:**
```java
textToSpeech = new TextToSpeech(requireContext(), status -> {
    if (status == TextToSpeech.SUCCESS) {
        textToSpeech.setLanguage(Locale.US);
    }
});

// Phát âm từ
textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, null);
```

#### 3.1.4 Lưu Từ Vựng
**Trạng thái:** ✅ Hoàn tất

**Quy Trình:**
1. Kiểm tra từ đã tồn tại trong DB?
2. Nếu chưa → Tạo LearnedWordEntity
3. Insert vào Room Database
4. Cập nhật UserStats (totalWordsLearned++)

**Code:**
```java
saveButton.setOnClickListener(v -> {
    repository.saveWord(new WordEntry(
        currentResult.getWord(),
        currentResult.getIpa(),
        currentResult.getMeaning(),
        currentResult.getExample(),
        currentResult.getCategory()
    ));
});
```

### 3.2 Luồng Hoạt Động (User Flow)

```
┌─────────────────────────────────────────────────┐
│  1. Mở Chức Năng Scan                          │
├─────────────────────────────────────────────────┤
│  2. Chọn ảnh từ:                               │
│     ├─ Chụp ảnh (camera)                       │
│     └─ Chọn từ thư viện                        │
├─────────────────────────────────────────────────┤
│  3. Click "Phân Tích AI"                       │
├─────────────────────────────────────────────────┤
│  4. Hệ thống hiển thị kết quả:                 │
│     ├─ Từ + IPA + Nghĩa                        │
│     ├─ Ví dụ câu                               │
│     ├─ Danh mục                                │
│     ├─ Fun Fact                                │
│     └─ Từ liên quan                            │
├─────────────────────────────────────────────────┤
│  5. Người dùng có thể:                         │
│     ├─ Phát âm từ (🔊 button)                  │
│     ├─ Lưu từ (💾 button)                      │
│     └─ Quét từ khác                            │
└─────────────────────────────────────────────────┘
```

---

## IV. GIAO DIỆN (UI)

### 4.1 Layout Components

```
fragment_scan.xml
├── ImageView (scanPreview)           - Hiển thị ảnh
├── Button (btnTakePhoto)             - Chụp ảnh
├── Button (btnPickGallery)           - Chọn thư viện
├── Button (btnAnalyzeAi)             - Phân tích AI
├── CardView
│   ├── TextView (scanWord)           - Không từ
│   ├── TextView (scanIpaMeaning)     - IPA + nghĩa
│   ├── TextView (scanExample)        - Ví dụ
│   ├── TextView (scanCategory)       - Danh mục
│   ├── TextView (scanFunFact)        - Fun Fact
│   ├── TextView (scanRelated)        - Từ liên quan
│   ├── Button (btnPronounceScan)     - Phát âm
│   └── Button (btnSaveScan)          - Lưu từ
```

### 4.2 Wireframe

```
┌──────────────────────────────────┐
│ 📸 Quét Từ Vựng                 │
├──────────────────────────────────┤
│ ┌────────────────────────────┐   │
│ │  [Preview Ảnh]      [🔊]   │   │
│ └────────────────────────────┘   │
├──────────────────────────────────┤
│ [📷 Chụp]  [🖼️ Thư Viện]       │
├──────────────────────────────────┤
│ 🔍 [    Phân Tích AI    ]        │
├──────────────────────────────────┤
│                                  │
│ ┌────────────────────────────┐   │
│ │ bottle                  🔊 │   │
│ │ /ˈbɒt.əl/ • chai, lọ     │   │
│ │ [Nhà Cửa]                 │   │
│ │                           │   │
│ │ 💬 Ví dụ:                │   │
│ │ Please recycle...bottle  │   │
│ │                           │   │
│ │ 🎓 Fun Fact:             │   │
│ │ The word 'bottle'...     │   │
│ │                           │   │
│ │ 🔗 glass, container...  │   │
│ │                           │   │
│ │ [🔊 Phát Âm] [💾 Lưu]   │   │
│ └────────────────────────────┘   │
└──────────────────────────────────┘
```

---

## V. CỞ SỞ DỮ LIỆU

### 5.1 Entities

#### 5.1.1 LearnedWordEntity
```
┌─────────────────────────────────┐
│  LearnedWordEntity              │
├─────────────────────────────────┤
│  @PrimaryKey id: Long           │
│  word: String (UNIQUE)          │
│  ipa: String                    │
│  meaning: String                │
│  example: String                │
│  category: String               │
│  dateAdded: Long                │
│  reviewCount: Int               │
└─────────────────────────────────┘
```

#### 5.1.2 UserStatsEntity
```
┌─────────────────────────────────┐
│  UserStatsEntity                │
├─────────────────────────────────┤
│  @PrimaryKey userId: String     │
│  totalWordsScanned: Int         │
│  totalWordsLearned: Int         │
│  lastScanDate: Long             │
│  totalPracticeSessions: Int     │
└─────────────────────────────────┘
```

### 5.2 Các Phương Thức Repository
| Phương Thức | Mô Tả |
|------------|-------|
| `mockScanResult()` | Trả dữ liệu mẫu |
| `saveWord(WordEntry)` | Lưu từ vựng vào DB |
| `increaseScanCount()` | Tăng bộ đếm quét |
| `getSavedWords()` | Lấy danh sách từ đã lưu |
| `getScannedCount()` | Lấy tổng số từ quét |

---

## VI. CÔNG NGHỆ & DEPENDENCIES

### 6.1 Android APIs
- TextToSpeech (API 1+)
- ActivityResultLauncher (API 26+)
- Fragment (AndroidX)
- ContentProvider

### 6.2 Libraries
- **Room** 2.6.1 - Database ORM
- **Material Design 3** 1.12.0 - UI Components
- **Kotlin Coroutines** - Async operations
- **ExecutorService** - Background threads

### 6.3 API Services (Cần tích hợp)
- **Google Generative AI (Gemini)** - Phân tích ảnh
- **Cloud Vision API** - OCR (Optional)

---

## VII. TÍNH NĂNG HIỆN CÓ vs HOẠT ĐỘNG

### 7.1 Bảng Trạng Thái

| Tính Năng | Trạng Thái | Mô Tả |
|-----------|-----------|-------|
| Chọn từ thư viện | ✅ Hoàn tất | ActivityResultLauncher |
| Hiển thị kết quả | ✅ Hoàn tất | Mock data từ "bottle" |
| Phát âm từ | ✅ Hoàn tất | TextToSpeech API |
| Lưu từ vựng | ✅ Hoàn tất | Room DB + Stats |
| Chụp ảnh | ⏳ Demo | Chưa tích hợp CameraX |
| Phân tích AI | ⏳ Demo | Chưa tích hợp Gemini |
| OCR | ❌ Chưa có | Cần thêm |
| Lịch sử quét | ❌ Chưa có | Cần thêm |

### 7.2 Tính Năng Hoàn Tất
✅ **Chọn ảnh từ thư viện** - Sử dụng ActivityResultLauncher
✅ **Hiển thị kết quả** - Cập nhật UI components
✅ **Phát âm TextToSpeech** - Ngôn ngữ Tiếng Anh (US)
✅ **Lưu từ vựng** - Room Database + Thống kê

### 7.3 Tính Năng Demo (Cần Tích Hợp)
⏳ **Chụp ảnh trực tiếp** - Cần CameraX integration
⏳ **Phân tích AI** - Cần Gemini API integration
⏳ **OCR thực** - Cần Vision API hoặc ML Kit

---

## VIII. ĐIỂM MẠNH & ĐIỂM YẾU

### 8.1 Điểm Mạnh ✅
- Giao diện thân thiện, trực quan
- Kiến trúc sạch (Repository Pattern)
- Xử lý bất đồng bộ đúng cách (ExecutorService)
- Tích hợp TextToSpeech cho phát âm
- Lưu dữ liệu chính xác vào Database
- Không lưu trùng từ vựng
- Tracking thống kê người dùng

### 8.2 Điểm Yếu ❌
- Chủ yếu là mock data (demo)
- Chưa tích hợp AI thực tế
- Chưa có camera capture
- Chưa có lịch sử quét
- Chưa có hình ảnh tối ưu hóa
- Chưa có offline support tối ưu

---

## IX. HƯỚNG PHÁT TRIỂN TIẾP THEO

### 9.1 Cần Thực Hiện (High Priority)
1. **Tích hợp Gemini AI** - Thay thế mock data
   - Gọi API với hình ảnh
   - Parse JSON response
   
2. **Tích hợp CameraX** - Chụp ảnh trực tiếp
   - Request permission
   - Preview & capture
   
3. **OCR Integration** - Nhận dạng text
   - Dùng ML Kit hoặc Cloud Vision

### 9.2 Tính Năng Bổ Sung (Medium Priority)
4. Lịch sử quét từ
5. Favorites / Bookmarks
6. Statistics dashboard
7. Spaced Repetition (SRS)
8. Export từ vựng

### 9.3 Tối Ưu Hóa (Low Priority)
9. Caching hình ảnh
10. Offline support
11. Performance optimization
12. Error handling cải thiện

---

## X. KẾT LUẬN

Chức năng **Scan** là một phần quan trọng của ứng dụng EnglishFlow, giúp người dùng nhanh chóng học từ vựng mới. Hiện tại có nền tảng kiến trúc vững chắc nhưng cần tích hợp AI thực tế để hoạt động đầy đủ.

### Tóm Tắt:
- ✅ **Architecture:** Tốt (MVVM + Repository)
- ✅ **Current Features:** Hoàn tất (Chọn ảnh, phát âm, lưu từ)
- ⏳ **AI Integration:** Demo (Mock data)
- 📈 **Next Steps:** Integrate Gemini API + CameraX

---

## PHỤ LỤC

### A. Ví Dụ Dữ Liệu Mock
```json
{
  "word": "bottle",
  "ipa": "/ˈbɒt.əl/",
  "meaning": "chai, lọ",
  "example": "Please recycle this plastic bottle.",
  "category": "Nhà cửa",
  "funFact": "The word 'bottle' comes from Medieval Latin 'butticula'.",
  "relatedWords": ["glass", "container", "liquid"]
}
```

### B. Stack Công Nghệ
```
Frontend:     Android (Kotlin/Java)
Architecture: MVVM + Repository
Database:     Room (SQLite)
APIs:         TextToSpeech, Camera, Generative AI
```

### C. Liên Hệ
- **Repository:** AppRepository.java
- **Fragment:** ScanFragment.java
- **Database:** DonEnglishQuestDatabase.java

---

**Generated:** 2026-03-24  
**Version:** 1.0
