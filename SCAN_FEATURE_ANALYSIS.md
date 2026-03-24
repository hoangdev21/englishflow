# Phân Tích Chức Năng Scan - EnglishFlow App

## 📋 Tóm Tắt Chức Năng

Chức năng **Scan** trong ứng dụng EnglishFlow cho phép người dùng quét (scan) hình ảnh chứa từ vựng tiếng Anh và nhận được thông tin chi tiết về từ đó, bao gồm cách phát âm, nghĩa, ví dụ sử dụng, và các từ liên quan.

---

## 🏗️ Kiến Trúc Kỹ Thuật

### File Chính
- **ScanFragment.java** - Fragment chính quản lý UI và logic scan
- **ScanResult.java** - Model lưu trữ kết quả quét
- **WordEntry.java** - Model lưu trữ thông tin từ vựng
- **AppRepository.java** - Repository chứa logic xử lý dữ liệu

### Kiến Trúc:
- **Fragment-based UI** - Sử dụng Android Fragment cho giao diện
- **Repository Pattern** - Tách biệt logic dữ liệu từ UI
- **Room Database** - Lưu trữ từ vựng đã quét
- **SharedPreferences** - Lưu trữ cài đặt người dùng

---

## 💻 Thành Phần Chính

### 1. **ScanFragment.java**
Lớp Fragment quản lý giao diện và tương tác người dùng

#### Các thành phần giao diện:
```
- ImageView (scanPreview): Hiển thị ảnh đã chọn/chụp
- TextView (scanWord): Hiển thị từ vựng
- TextView (scanIpaMeaning): Hiển thị IPA và nghĩa
- TextView (scanExample): Hiển thị câu ví dụ
- TextView (scanCategory): Hiển thị danh mục từ
- TextView (scanFunFact): Hiển thị sự thật thú vị về từ
- TextView (scanRelated): Hiển thị các từ liên quan
- MaterialButton (btnTakePhoto): Nút chụp ảnh
- MaterialButton (btnPickGallery): Nút chọn từ thư viện
- MaterialButton (btnAnalyzeAi): Nút phân tích bằng AI
- MaterialButton (btnPronounceScan): Nút phát âm
- MaterialButton (btnSaveScan): Nút lưu từ vựng
```

#### Các chức năng chính:
- **onCreateView()**: Khởi tạo view từ layout
- **onViewCreated()**: Khởi tạo components, đăng ký listeners
- **handleImagePicked()**: Xử lý ảnh được chọn từ thư viện
- **bindResult()**: Hiển thị kết quả quét lên UI

---

### 2. **ScanResult.java**
Model lưu trữ kết quả quét

#### Thuộc tính:
```java
- String word          // Từ vựng
- String ipa           // Phát âm IPA
- String meaning       // Nghĩa tiếng Việt
- String example       // Câu ví dụ
- String category      // Danh mục (Nhà cửa, Lớp học, v.v.)
- String funFact       // Sự thật thú vị về từ
- List<String> relatedWords  // Danh sách từ liên quan (3 từ)
```

#### Ví dụ dữ liệu:
```
Word: bottle
IPA: /ˈbɒt.əl/
Meaning: chai, lọ
Example: Please recycle this plastic bottle.
Category: Nhà cửa
FunFact: The word 'bottle' comes from Medieval Latin 'butticula'.
RelatedWords: [glass, container, liquid]
```

---

### 3. **WordEntry.java**
Model để lưu từ vựng vào cơ sở dữ liệu

#### Thuộc tính:
```java
- String word      // Từ vựng
- String ipa       // Phát âm IPA
- String meaning   // Nghĩa tiếng Việt
- String example   // Câu ví dụ
- String category  // Danh mục
```

---

### 4. **AppRepository.java**
Lớp Repository quản lý truy cập dữ liệu

#### Các phương thức chính liên quan đến Scan:

**a) mockScanResult()**
```java
public ScanResult mockScanResult() {
    // Tạo dữ liệu mẫu cho từ "bottle"
    // Trả về ScanResult với đầy đủ thông tin
}
```
- **Mục đích**: Cung cấp dữ liệu mẫu để demo (hiện tại là từ "bottle")
- **Trạng thái**: Dùng mock data, chưa tích hợp với AI thực

**b) saveWord(WordEntry)**
```java
public void saveWord(WordEntry wordEntry) {
    // Chạy trên background thread (ExecutorService)
    // Kiểm tra nếu từ đã tồn tại -> bỏ qua
    // Nếu chưa tồn tại -> lưu vào database
    // Cập nhật stats: totalWordsLearned ++
}
```
- **Mục đích**: Lưu từ vựng từ quét vào database
- **Quy trình**:
  1. Kiểm tra xem từ đã có trong DB chưa
  2. Tạo LearnedWordEntity
  3. Insert vào database
  4. Cập nhật thống kê người dùng

**c) increaseScanCount()**
```java
public void increaseScanCount() {
    // Chạy trên background thread
    // Lấy thống kê hiện tại từ DB
    // Tăng totalWordsScanned lên 1
    // Cập nhật lại database
}
```
- **Mục đích**: Tăng tổng số từ đã quét của người dùng
- **Thời điểm gọi**: Mỗi khi người dùng click nút "Phân tích AI"

**d) getSavedWords()**
```java
public List<WordEntry> getSavedWords()
```
- **Mục đích**: Lấy danh sách tất cả từ vựng đã lưu từ scan

---

## 🎯 Luồng Hoạt Động

### Quy trình Scan một từ:

```
1. Người dùng click "Chụp ảnh" hoặc "Chọn từ thư viện"
       ↓
2. Chọn/chụp ảnh chứa từ vựng
       ↓
3. Click "Phân tích AI"
       ↓
4. Hệ thống lấy dữ liệu mẫu (mockScanResult)
       ↓
5. Tăng bộ đếm quét (increaseScanCount)
       ↓
6. Hiển thị kết quả:
   - Từ (word)
   - Phát âm (IPA)
   - Nghĩa (meaning)
   - Ví dụ (example)
   - Danh mục (category)
   - Sự thật thú vị (funFact)
   - Từ liên quan (relatedWords)
       ↓
7. Người dùng có thể:
   a) Click phát âm (TextToSpeech)
   b) Click lưu từ → Lưu vào database
   c) Quét từ khác → Quay lại bước 1
```

---

## 🔊 Tính Năng Chi Tiết

### 1. **Chụp/Chọn Ảnh**

**Chụp ảnh (Take Photo)**
```java
takePhotoButton.setOnClickListener(v -> 
    Toast.makeText(requireContext(), 
        "Demo: mở camera sẽ có ở bản tích hợp AI thật", 
        Toast.LENGTH_SHORT).show()
);
```
- **Trạng thái**: Chưa thực hiện, chỉ hiển thị thông báo demo

**Chọn từ thư viện (Pick Gallery)**
```java
pickGalleryButton.setOnClickListener(v -> 
    imagePickerLauncher.launch("image/*")
);
```
- **Hoạt động**: 
  - Sử dụng ActivityResultLauncher
  - Mở intent chọn ảnh từ thư viện thiết bị
  - Gọi handleImagePicked() khi ảnh được chọn

### 2. **Phân Tích Ảnh (Analyze)**

```java
analyzeButton.setOnClickListener(v -> {
    repository.increaseScanCount();           // Tăng số lần quét
    currentResult = repository.mockScanResult();  // Lấy kết quả
    bindResult(currentResult);                // Hiển thị kết quả
    Toast.makeText(requireContext(), 
        "AI đã phân tích xong hình ảnh", 
        Toast.LENGTH_SHORT).show();
});
```
- **Chức năng**:
  1. Tăng bộ đếm quét
  2. Lấy kết quả (hiện là mock data)
  3. Cập nhật UI với kết quả mới

### 3. **Phát Âm (Pronunciation)**

```java
pronounceButton.setOnClickListener(v -> 
    textToSpeech.speak(
        currentResult.getWord(), 
        TextToSpeech.QUEUE_FLUSH, 
        null, 
        "scan-word"
    )
);
```
- **Công nghệ**: Android TextToSpeech API
- **Ngôn ngữ**: Tiếng Anh (Locale.US)
- **Hoạt động**: Phát âm từ vựng chi phôi

### 4. **Lưu Từ Vựng (Save)**

```java
saveButton.setOnClickListener(v -> {
    repository.saveWord(new WordEntry(
        currentResult.getWord(),
        currentResult.getIpa(),
        currentResult.getMeaning(),
        currentResult.getExample(),
        currentResult.getCategory()
    ));
    Toast.makeText(requireContext(), 
        "Đã lưu từ scan vào từ điển", 
        Toast.LENGTH_SHORT).show();
});
```
- **Quy trình**:
  1. Tạo WordEntry object
  2. Gọi repository.saveWord()
  3. Từ được lưu vào Room Database
  4. Thống kê totalWordsLearned tăng lên

---

## 📊 Tương Tác Dữ Liệu

### Database: Room

#### Entities liên quan:
1. **LearnedWordEntity** - Lưu từ vựng đã học
   - Columns: word, ipa, meaning, example, domain, ...

2. **UserStatsEntity** - Lưu thống kê người dùng
   - totalWordsScanned - Tổng số từ đã quét
   - totalWordsLearned - Tổng số từ đã học
   - Các stats khác...

#### Quan hệ:
```
ScanFragment
    ↓
AppRepository
    ↓
↙━━━━┴━━━━━━╋━━━━━━━╋━━━━━━━━━╋━━━━━━━━━━━━╋━━━━━━━━━╋━━━━━━━━━━━━╋━
LearnedWordDao  UserStatsDao  ...
    ↓              ↓
LearnedWordEntity  UserStatsEntity
```

---

## 🎨 Giao Diện (UI Layout)

### Layout: fragment_scan.xml

```
┌─────────────────────────────────────────────┐
│  📸 Quét Từ Vựng (Scan Title)              │
├─────────────────────────────────────────────┤
│  ┌───────────────────────────────────────┐  │
│  │                                       │  │
│  │  [Camera Icon]  Dùng camera để    │  │
│  │                 chụp từ vựng        │  │
│  │                                       │  │
│  └───────────────────────────────────────┘  │
├─────────────────────────────────────────────┤
│  [Chụp Ảnh]      [Chọn Thư Viện]         │
├─────────────────────────────────────────────┤
│  [      Phân Tích AI      ]                │
├─────────────────────────────────────────────┤
│  ┌─────────────────────────────────────┐   │
│  │ 📸  Từ được quét                   │ 🔊 │
│  │     Chi tiết từ vựng                │   │
│  ├─────────────────────────────────────┤   │
│  │                                     │   │
│  │  bottle                             │   │
│  │  /ˈbɒt.əl/ • chai, lọ              │   │
│  │                                     │   │
│  │  [Nhà cửa]                          │   │
│  │                                     │   │
│  │  💬 Ví dụ:                          │   │
│  │  Please recycle this plastic        │   │
│  │  bottle.                            │   │
│  │                                     │   │
│  │  🎓 Fun Fact:                       │   │
│  │  The word 'bottle' comes from       │   │
│  │  Medieval Latin 'butticula'.        │   │
│  │                                     │   │
│  │  🔗 Từ liên quan:                   │   │
│  │  glass, container, liquid           │   │
│  │                                     │   │
│  │  [       Phát Âm      ]             │   │
│  │  [    Lưu Vào Từ Điển  ]            │   │
│  │                                     │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

---

## ⚡ Quy Trình Kỹ Thuật Chi Tiết

### 1. Khởi tạo Fragment

```
onCreateView()
    ↓ 
Inflate layout (fragment_scan.xml)
    ↓
onViewCreated()
    ↓
  ├─ Repository.getInstance()
  ├─ Bind UI elements
  ├─ Register image picker launcher
  ├─ Load mock data
  ├─ Setup TextToSpeech
  └─ Attach event listeners
```

### 2. Chọn Ảnh

```
pickGalleryButton.click()
    ↓
imagePickerLauncher.launch("image/*")
    ↓
User selects image
    ↓
handleImagePicked(Uri)
    ↓
previewImage.setImageURI(uri)
    ↓
Show toast: "Ảnh đã được chọn"
```

### 3. Phân Tích & Lưu

```
analyzeButton.click()
    ↓
repository.increaseScanCount()
    ├─ ExecutorService.execute()
    ├─ Get UserStatsEntity from DB
    └─ Update totalWordsScanned++
    ↓
currentResult = repository.mockScanResult()
    ↓
bindResult(currentResult)
    ├─ scanWord.setText(word)
    ├─ scanIpaMeaning.setText(ipa + meaning)
    ├─ scanExample.setText(example)
    ├─ scanCategory.setText(category)
    ├─ scanFunFact.setText(funFact)
    └─ scanRelated.setText(relatedWords)
    ↓
Show toast: "AI đã phân tích xong"
```

### 4. Lưu Từ Vựng

```
saveButton.click()
    ↓
repository.saveWord(WordEntry)
    ├─ ExecutorService.execute()
    ├─ Check if word exists in DB
    ├─ If not exists:
    │  ├─ Create LearnedWordEntity
    │  ├─ Insert to DB
    │  └─ Update UserStatsEntity.totalWordsLearned++
    └─ If exists: return (không lưu trùng)
    ↓
Show toast: "Đã lưu từ vào từ điển"
```

---

## 📈 Thống Kê & Metrics

### Người dùng có thể theo dõi:

1. **Total Words Scanned** - Tổng số từ đã quét
   - Tăng mỗi khi click "Phân tích AI"

2. **Total Words Learned** - Tổng số từ đã học
   - Tăng mỗi khi click "Lưu từ vựng"
   - Không lưu trùng (same word)

---

## 🚀 Các Tính Năng Hiện Có

| Tính Năng | Trạng Thái | Ghi Chú |
|-----------|-----------|--------|
| Chỉnh ảnh từ thư viện | ✅ Hoàn tất | Sử dụng ActivityResultLauncher |
| Hiển thị kết quả | ✅ Hoàn tất | Mock data từ "bottle" |
| Phát âm từ vựng | ✅ Hoàn tất | TextToSpeech API |
| Lưu từ vựng | ✅ Hoàn tất | Room Database + Stats |
| Chụp ảnh trực tiếp | ⏳ Demo | Chưa tích hợp camera |
| Phân tích AI thực | ⏳ Demo | Chưa tích hợp Gemini API |

---

## 🔧 Công Nghệ & Dependencies

### Android APIs:
- **TextToSpeech** - Phát âm từ vựng
- **ActivityResultLauncher** - Chọn ảnh từ thư viện
- **Fragment** - Quản lý UI

### Thư viện:
- **Room** - Database ORM
- **Material Design 3** - UI components (MaterialButton, MaterialCardView)
- **SharedPreferences** - Lưu cài đặt
- **ExecutorService** - Background threads

---

## 📝 Code Snippets Quan Trọng

### Khởi tạo TextToSpeech:
```java
textToSpeech = new TextToSpeech(requireContext(), status -> {
    if (status == TextToSpeech.SUCCESS) {
        textToSpeech.setLanguage(Locale.US);
    }
});
```

### Sử dụng ImagePickerLauncher:
```java
imagePickerLauncher = registerForActivityResult(
    new ActivityResultContracts.GetContent(), 
    this::handleImagePicked
);
```

### Tạo WordEntry và lưu:
```java
WordEntry entry = new WordEntry(
    "bottle",
    "/ˈbɒt.əl/",
    "chai, lọ",
    "Please recycle this plastic bottle.",
    "Nhà cửa"
);
repository.saveWord(entry);
```

---

## 🎓 Bài Học & Best Practices

1. **Asynchronous Operations**: Sử dụng ExecutorService để không block main thread
2. **Separation of Concerns**: Repository pattern tách biệt UI logic từ data logic
3. **Resource Management**: Cleanup TextToSpeech trong onDestroyView()
4. **User Feedback**: Toast messages cho mỗi hành động
5. **Mock Data**: Sử dụng dữ liệu mẫu để test trước khi tích hợp API thực

---

## 🔮 Hướng Phát Triển Tiếp Theo

1. **Tích hợp Camera**: Sử dụng CameraX API cho chụp ảnh trực tiếp
2. **Tích hợp Gemini AI**: Gọi Google Generative AI để phân tích ảnh thực
3. **OCR (Optical Character Recognition)**: Nhận dạng text từ ảnh
4. **Cập nhật Database**: Lưu ảnh gốc và thông tin chi tiết khác
5. **Phân tích Chuyên Sâu**: Hiển thị thêm ví dụ, bồi cơ, từ phát âm

---

## 📌 Kết Luận

Chức năng **Scan** là một phần quan trọng của ứng dụng EnglishFlow, cho phép người dùng nhanh chóng học từ vựng mới bằng cách chụp hoặc chọn ảnh. Hiện tại, nó sử dụng dữ liệu mẫu (mock data) nhưng có khung cấu trúc tốt để tích hợp AI thực tế trong tương lai.

**Điểm mạnh:**
- Giao diện thân thiện, dễ sử dụng
- Kiến trúc sạch (Repository Pattern)
- Tích hợp TextToSpeech cho phát âm
- Lưu dữ liệu đúng cách vào Database

**Điểm cần cải thiện:**
- Chủ yếu là mock data (demo)
- Cần tích hợp với AI service thực tế
- Cần thêm camera capture feature
