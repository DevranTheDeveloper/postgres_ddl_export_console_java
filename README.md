# PostgreSQL DDL Studio (v5.5.4)

[![Build and Release Multi-OS](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/actions/workflows/release.yml/badge.svg)](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![PostgreSQL 12+](https://img.shields.io/badge/PostgreSQL-12%2B-336791.svg)](https://www.postgresql.org/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/pulls)

**PostgreSQL DDL Studio**, modern PostgreSQL veritabanları için geliştirilmiş; interaktif şema gezgini, görsel ERD ilişki haritası, evrensel Docker ve bulut (Neon, Supabase, AWS RDS) bağlantı asistanı, canlı sunucu performans izleme paneli, çoklu ortam şema farkı (Diff) motoru, arka plan zamanlanmış otomatik yedekleme (Cron) ve kurumsal düzeyde AES-256 GCM güvenlik kalkanı sunan **çok platformlu (Cross-Platform)** profesyonel bir masaüstü geliştirici stüdyosudur.

---

## 📦 İndirme & Sürümler (Downloads & Releases)

Tüm platformlar için en son derlenmiş paketlere **[GitHub Releases Sayfasından](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/latest)** veya doğrudan aşağıdaki bağlantılardan erişebilirsiniz:

| Platform | Format / Paket | Doğrudan İndirme Bağlantısı | Açıklama |
| :--- | :--- | :--- | :--- |
| 🪟 **Windows** | **`.exe` Kurulum Sihirbazı** | [📥 **PostgreSQL-DDL-Studio-Setup-5.5.4.exe**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/PostgreSQL-DDL-Studio-Setup-5.5.4.exe) | ⭐ **Önerilen**: İleri-İleri Kurulum Sihirbazı, Masaüstü/Başlat Simgeleri |
| 🍏 **macOS** | **`.dmg` Yükleyici** | [📥 **PostgreSQL-DDL-Studio-5.5.4-macOS.dmg**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/PostgreSQL-DDL-Studio-5.5.4-macOS.dmg) | Apple Silicon (M1-M4) & Intel, Sürükle-Bırak Kurulum |
| 🐧 **Linux** | **`.deb` Kurulum Paketi** | [📥 **PostgreSQL-DDL-Studio-5.5.4-Linux.deb**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/PostgreSQL-DDL-Studio-5.5.4-Linux.deb) | ⭐ Kali, Ubuntu, Debian için tek tıkla kurulum |
| 🐧 **Linux** | **Taşınabilir `.tar.gz`** | [📥 **PostgreSQL-DDL-Studio-5.5.4-Linux.tar.gz**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/PostgreSQL-DDL-Studio-5.5.4-Linux.tar.gz) | Kali, Ubuntu, Debian, Fedora, Arch (`run.sh` / `install.sh`) |
| 🪟 **Windows** | **Taşınabilir `.zip`** | [📥 **PostgreSQL-DDL-Studio-5.5.4-Windows.zip**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/PostgreSQL-DDL-Studio-5.5.4-Windows.zip) | Kurulum gerektirmeyen taşınabilir (Portable) paket |
| ☕ **Evrensel JAR** | **Fat `.jar`** | [📥 **postgres_ddl_export_console_java-1.0.0.jar**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/postgres_ddl_export_console_java-1.0.0.jar) | Tüm platformlar, CLI ve Sunucu otomasyonu |
| 🔒 **Güvenlik** | **SHA-256 Sağlama** | [📄 **SHA256SUMS.txt**](https://github.com/DevranTheDeveloper/postgres_ddl_export_console_java/releases/download/v5.5.4/SHA256SUMS.txt) | Kriptografik bütünlük doğrulama |

---

## 🚀 Öne Çıkan Yetenekler & Özellikler

### 1. ⚡ Evrensel Veritabanı & Docker Asistanı (Universal DB Hub)
- **Konteyner Keşfi:** Bilgisayardaki tüm Docker PostgreSQL konteynerlerini otomatik tarar ve listeler.
- **Tek Tıkla Demo Başlat:** Sıfırdan tek tıkla PostgreSQL 16 konteyneri ayağa kaldırır.
- **Bulut Bağlantı URL (URI Parser):** Neon, Supabase, AWS RDS, Render, Railway vb. bağlantı dizelerini (`postgresql://...`) tek tıkla çözüp bağlanır.
- **Yerel Servis Algılama:** Homebrew, Postgres.app veya Windows servisi olarak çalışan `localhost:5432` portunu otomatik bağlar.
- **Örnek Şema Enjeksiyonu (Seed Data):** Boş veritabanlarına tek tıkla E-Ticaret, SaaS veya Blog örnek tablolarını yükler.

### 2. 📂 İnteraktif Şema & SQL Gezgini
- **Ağaç Tabanlı Nesne Gezgini:** Tablolar, Görünümler (View), Fonksiyonlar, Saklı Yordamlar, İndeksler, Diziler (Sequence) ve Tipleri klasörlenmiş hiyerarşide listeler.
- **Dahili SQL Editörü:** DDL betiklerini anlık düzenleme, satır numaraları, arama/filtreleme ve sözdizimi biçimlendirme (`[ Formatla ]`).
- **Disk ve ZIP Arşivleme:** Şema klasörünü tek tıkla `.zip` arşivi olarak dışa aktarma (`[ ZIP Arşivle ]`).

### 3. 🗺️ Görsel İlişki Haritası (Interactive ER Diagram)
- **Dinamik Java2D Canvas:** Panning (sürükle-bırak), fare tekerleğiyle Smooth Zooming (%20 - %300).
- **Yumuşak Bézier Eğrileri:** Tablolar arasındaki Foreign Key (FK) ilişkilerini yumuşak Bézier eğrileri ile görselleştirir.
- **Dışa Aktarma:** Mermaid Markdown kodunu panoya kopyalama (`[ Mermaid Kopyala ]`) veya yüksek çözünürlüklü PNG (`[ PNG İndir ]`) çıktısı alma.

### 4. 🌐 Çoklu Ortam Şema Farkı & Dağıtım Motoru (Live Diff Engine)
- **Staging vs Production Karşılaştırma:** İki farklı canlı veritabanı profilini (örneğin Test ile Canlı) yan yana koyup nesne bazında farkları tespit eder.
- **Otomatik Dağıtım Yaması (Deploy Patch):** Eksik veya değişen tabloları hedef ortamla eşitleyecek SQL yamasını tek tıkla üretir.
- **Kritik Veri Kaybı Koruması:** `DROP TABLE`, `DROP COLUMN`, `TRUNCATE` ve `CASCADE` öncesinde güvenlik onayı gösterir.

### 5. 📊 Canlı PostgreSQL Sunucu Durumu & Metrikler
- **Gerçek Zamanlı KPI Kartları:** Aktif bağlantı havuzu, toplam veritabanı boyutu, önbellek verimliliği (Cache Hit Rate %) ve işlem istatistikleri.
- **Java2D Performans Grafiği:** Canlı Commit / Rollback ve Önbellek oranlarını dinamik grafiklerle görselleştirir.
- **Canlı Oturum Tablosu:** Sunucuda o an koşan sorguları (`pg_stat_activity`), tablo boyutlarını ve işlem geçmişini canlı izleme.

### 6. ⏱️ Otomatik Zamanlanmış Yedekleme & Cron Motoru
- **Esnek Zamanlama:** 15 dk, 30 dk, 1 saat, 6 saat veya 24 saatlik aralıklarla arka planda otomatik DDL yedeği alma.
- **Otomatik Git Senkronizasyonu:** Yedekleme sonrasında DDL değişikliklerini otomatik olarak Git deposuna commit edebilme.

### 7. 🛡️ Kurumsal Güvenlik Kalkanı
- **AES-256 GCM Parola Koruması:** `profiles.json` içinde şifreler asla düz metin (plain text) saklanmaz; makine anahtarıyla şifrelenir (`ENC(...)`).
- **Git Sızıntı Kalkanı:** Hassas bağlantı ve şema dosyaları `.gitignore` ile korunur.

### 8. 🎨 Çok Platformlu Yerel Tema Desteği
- macOS için `FlatMacDarkLaf` / `FlatMacLightLaf`, Windows ve Linux için `FlatDarkLaf` / `FlatLightLaf`.
- İşletim sisteminizin açık/koyu temasını otomatik senkronize eder.

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

## 🛠️ Platformlara Göre Başlatma

### 🍏 macOS
Masaüstündeki **`PostgreSQL DDL Studio.app`** simgesine veya `.dmg` dosyasından yüklenen uygulamaya çift tıklayarak başlatabilirsiniz.

### 🪟 Windows
İndirdiğiniz `.zip` arşivini açın ve:
- **`PostgreSQL-DDL-Studio.vbs`** dosyasına çift tıklayarak konsol penceresi olmadan doğrudan başlatabilirsiniz.
- veya **`PostgreSQL-DDL-Studio.bat`** dosyasını çalıştırabilirsiniz.

### 🐧 Linux
İndirdiğiniz `.tar.gz` arşivini açın ve terminalden:
```bash
chmod +x run.sh
./run.sh
```

### ☕ Komut Satırı (CLI) Modu
```bash
java -jar PostgreSQL-DDL-Studio.jar -db:POSTGRESQL -od:./export_output -s:profiles.json
```

---

## 📄 Lisans

Bu proje MIT lisansı ile korunmaktadır. Devran Sever tarafından geliştirilmiştir.
