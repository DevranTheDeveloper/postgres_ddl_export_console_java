# PostgreSQL DDL Studio (v5.0.0)

**PostgreSQL DDL Studio**, modern PostgreSQL veritabanları için geliştirilmiş; interaktif şema gezgini, görsel ERD ilişki haritası, canlı sunucu performans izleme paneli, çoklu ortam şema farkı (Diff) motoru, arka plan zamanlanmış otomatik yedekleme (Cron) ve kurumsal düzeyde AES-256 GCM güvenlik kalkanı sunan profesyonel bir masaüstü geliştirici stüdyosudur.

---

## 🚀 Öne Çıkan Yetenekler & Özellikler

### 1. 📂 İnteraktif Şema & SQL Gezgini
- **Ağaç Tabanlı Nesne Gezgini:** Tablolar, Görünümler (View), Fonksiyonlar, Saklı Yordamlar, İndeksler, Diziler (Sequence) ve Tipleri klasörlenmiş hiyerarşide listeler.
- **Dahili SQL Editörü:** DDL betiklerini anlık düzenleme, satır numaraları, arama/filtreleme ve sözdizimi biçimlendirme (`[ Formatla ]`).
- **Disk ve ZIP Arşivleme:** Şema klasörünü tek tıkla `.zip` arşivi olarak dışa aktarma (`[ ZIP Arşivle ]`).
- **Otomatik Odaklanma:** Üst bardan profil değiştirildiğinde ilgili veritabanının ağaç düğümüne otomatik odaklanma ve genişletme.

### 2. 🗺️ Görsel İlişki Haritası (Interactive ER Diagram)
- **Dinamik Java2D Canvas:** Panning (sürükle-bırak), fare tekerleğiyle Smooth Zooming (%20 - %300).
- **Yumuşak Bézier Eğrileri:** Tablolar arasındaki Foreign Key (FK) ilişkilerini yumuşak Bézier eğrileri ile görselleştirir.
- **Taşınabilir Tablo Kartları:** Tablo kartlarını tuval üzerinde serbestçe sürükleyip yeniden konumlandırma.
- **Kısayol ve Navigasyon:** Tabloya çift tıklayarak SQL Gezgininde ilgili DDL dosyasını açma.
- **Dışa Aktarma:** Mermaid Markdown kodunu panoya kopyalama (`[ Mermaid Kopyala ]`) veya yüksek çözünürlüklü PNG (`[ PNG İndir ]`) çıktısı alma.

### 3. 🌐 Çoklu Ortam Şema Farkı & Dağıtım Motoru (Live Diff Engine)
- **Staging vs Production Karşılaştırma:** İki farklı canlı veritabanı profilini (örneğin Test ile Canlı) yan yana koyup nesne bazında farkları tespit eder.
- **Otomatik Dağıtım Yaması (Deploy Patch):** Eksik veya değişen tabloları hedef ortamla eşitleyecek SQL yamasını tek tıkla üretir.
- **Kritik Veri Kaybı Koruması:** `DROP TABLE`, `DROP COLUMN`, `TRUNCATE` ve `CASCADE` gibi yıkıcı operasyonlar öncesinde güvenlik onayı ve risk uyarısı gösterir.

### 4. 📊 Canlı PostgreSQL Sunucu Durumu & Metrikler
- **Gerçek Zamanlı KPI Kartları:** Aktif bağlantı havuzu, toplam veritabanı boyutu, önbellek verimliliği (Cache Hit Rate %) ve anlık işlem istatistikleri.
- **Java2D Performans Grafiği:** Canlı Commit / Rollback ve Önbellek oranlarını dinamik çubuk ve çizgi grafiklerle görselleştirir.
- **Canlı Oturum & Sorgu Tablosu:** Sunucuda o an koşan sorguları (`pg_stat_activity`), tablo boyutlarını ve işlem geçmişini canlı izleme.
- **Kapsamlı Sistem Teşhis (Diagnostics):** Bağlantı ve sunucu sağlığını otomatik analiz eden denetim penceresi.

### 5. ⏱️ Otomatik Zamanlanmış Yedekleme & Cron Motoru
- **Esnek Zamanlama:** 15 dk, 30 dk, 1 saat, 6 saat veya 24 saatlik aralıklarla arka planda otomatik DDL yedeği alma.
- **Otomatik Git Senkronizasyonu:** Yedekleme sonrasında DDL değişikliklerini otomatik olarak Git deposuna commit edebilme.
- **Canlı Durum ve Kolay Durdurma:** Üst bardan anlık durum rozeti takibi ve tek tıkla **`[ Zamanlayıcıyı Durdur ]`** / **`[ Kaydet & Başlat ]`** kontrolü.

### 6. 🛡️ Kurumsal Güvenlik Kalkanı
- **AES-256 GCM Parola Koruması:** `profiles.json` içinde şifreler asla düz metin (plain text) saklanmaz; makine anahtarıyla şifrelenir (`ENC(...)`).
- **Git Sızıntı Kalkanı:** Hassas bağlantı ve şema dosyaları `.gitignore` ile korunur; repoda yalnızca güvenli `profiles.example.json` şablonu yer alır.
- **Uzak Ağ SSL/TLS Kalkanı:** Uzak sunuculara şifresiz bağlanılmak istendiğinde kullanıcıyı uyararak `require` modunu teşvik eder.

### 7. 🎨 macOS Native Koyu / Açık Tema (`FlatMacDarkLaf`)
- macOS standartlarında pürüzsüz **Karanlık (Dark)** ve **Açık (Light)** tema desteği.
- **Tercih Kalıcılığı (Persistence):** Uygulamayı kapattığınız temayı hatırlar ve bir sonraki açılışta doğrudan o temayla başlar.

---

## ⌨️ Klavye Kısayolları

| Kısayol | İşlem |
| :--- | :--- |
| **⌘S / Ctrl+S** | Şema Gezgininde aktif SQL dosyasını kaydeder |
| **⌘F / Ctrl+F** | Şema Gezgininde arama çubuğuna odaklanır |
| **⌘E / Ctrl+E** | DDL dışa aktarım işlemini hemen başlatır |
| **⌘R / Ctrl+R** | Sunucu durumu metriklerini anında yeniler |
| **⌘D / Ctrl+D** | Şema Farkı (Diff) görünümüne geçiş yapar |
| **⌘T / Ctrl+T** | Koyu Tema / Açık Tema arasında geçiş yapar |
| **⌘1 - ⌘6** | Sekmeler arasında hızlı geçiş sağlar |

---

## 🛠️ Kurulum ve Çalıştırma

### Gereksinimler
- Java JDK 17+ (macOS Apple Silicon / Intel, Linux, Windows)
- Apache Maven 3.8+ (Kaynak koddan derlemek için)

### 1. Kaynak Koddan Derleme
```bash
mvn clean package
```

### 2. Konsol Modunda Çalıştırma (CLI)
```bash
java -jar target/postgres_ddl_export_console_java-1.0.0.jar -db:POSTGRESQL -od:./export_output -s:profiles.json
```

### 3. macOS Native Uygulama Olarak Çalıştırma (.app)
Masaüstündeki **`PostgreSQL DDL Studio.app`** simgesine çift tıklayarak yerel macOS penceresi olarak başlatabilirsiniz.

---

## 📄 Lisans

Bu proje MIT lisansı ile korunmaktadır.
