# PostgreSQL DDL Export Console (Java)

PostgreSQL veritabanındaki tüm nesnelerin (Schema, Table, View, Stored Procedure, Function, Index, Sequence, Type, Constraint, Trigger) DDL (Data Definition Language) `CREATE` script'lerini düzenli bir klasör yapısına export eden Java konsol uygulamasıdır.

Bu proje, [qk8/postgres_ddl_export_console_csharp](https://github.com/qk8/postgres_ddl_export_console_csharp) projesinin Java ile geliştirilmiş ve genişletilmiş versiyonudur.

---

## 🎯 Projenin Amacı ve Özellikleri

- **PostgreSQL 15.18+ Desteği**: PostgreSQL veritabanı nesnelerini eksiksiz dışa aktarır.
- **Modüler Mimari**: Farklı veritabanı motorlarını (MSSQL, MySQL, Oracle vb.) destekleyecek `IScripter`, `ScripterBuilder`, `IWriter` ve `IConfigurationReader` soyutlamalarına sahiptir.
- **Çift Çıkarma Motoru (Dual Engine Strategy)**:
  1. **`pg_dump` Engine**: Sistemde `pg_dump` / `pg_restore` araçları yüklüyse veritabanının birebir DDL yapısını yüksek sadakatle çıkartır.
  2. **JDBC Catalog Engine**: Sistemde `pg_dump` bulunmasa dahi doğrudan JDBC üzerinden `pg_catalog` ve `information_schema` sistem görünümlerini sorgulayarak DDL üreten saf Java (pure Java) yedek motor sunar.
- **Esnek Ayar Desteği**: JSON ayar dosyasındaki parametre isimlerini hem `camelCase` hem de `PascalCase` olarak okuyabilir.

---

## 📁 Çıktı Klasör Yapısı

```
<output_dir>/
└── <DatabaseName>/
    ├── SCHEMA/
    │   └── public.sql
    ├── TABLE/
    │   ├── public_Users.sql
    │   └── public_Orders.sql
    ├── VIEW/
    │   └── public_vw_ActiveUsers.sql
    ├── STORED_PROCEDURE/
    │   └── public_sp_get_user_by_id.sql
    ├── FUNCTION/
    │   └── public_fn_calculate_age.sql
    ├── INDEX/
    │   └── public_idx_users_email.sql
    ├── SEQUENCE/
    │   └── public_seq_user_id.sql
    ├── TYPE/
    │   └── public_status_enum.sql
    ├── CONSTRAINT/
    │   └── ...
    └── TRIGGER/
        └── ...
```

---

## ⚙️ Ayar Dosyası (`postgresql_settings.json`)

```json
{
  "serverHost": "localhost",
  "port": 5432,
  "databaseName": "denemeDatabase",
  "username": "postgres",
  "password": "12345",
  "schema": "public",
  "pgDumpPath": "pg_dump",
  "pgRestorePath": "pg_restore"
}
```

---

## 🚀 Derleme ve Çalıştırma

### Gereksinimler
- Java JDK 17 veya daha üstü
- Apache Maven 3.8+

### 1. Projeyi Derleme
```bash
mvn clean package
```
Derleme sonucunda `target/postgres_ddl_export_console_java-1.0.0.jar` çalıştırılabilir FAT JAR dosyası üretilir.

### 2. Uygulamayı Çalıştırma
```bash
java -jar target/postgres_ddl_export_console_java-1.0.0.jar -db:POSTGRESQL -od:./export_output -s:postgresql_settings.json
```

#### Parametreler:
- `-db:` Veritabanı tipi (Şu an için `POSTGRESQL`)
- `-od:` DDL dosyalarının yazılacağı hedef dizin
- `-s:` Bağlantı ve konfigürasyon JSON ayar dosyası

---

## 🧪 Testleri Çalıştırma

```bash
mvn test
```
