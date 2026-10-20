# DUCHAMP CINE — POCO X6 Pro 5G ("duchamp") Özel Sinema Kamerası

Bu proje **Xiaomi POCO X6 Pro 5G (Dimensity 8300-Ultra + Mali-G615 MC6 GPU)** modelinin 4 OmniVision kamera sensörüne özel olarak kalibre edilmiş Zero-ISP ve OpenGL ES 3.2 (GLSL 320 es) tabanlı sinematik kamera uygulamasıdır.

## Desteklenen POCO X6 Pro Donanım Modları
1. **0.6x 16mm Ultra Geniş Açı (OmniVision OV08D10):** Ters-fıçı (k1=-0.22) köşe düzeltmesi ve +%18 doygunluk kalibrasyonu.
2. **1x 25mm Ana Kamera (OmniVision OV64B40):** Zero-ISP (Denoise/Sharpening OFF), -0.7 EV highlight koruması ve OIS.
3. **2x 50mm Sinema Portre Lensi (OV64B40 In-Sensor Lossless):** 64MP sensör merkezinden kayıpsız 50mm optik kırpma.
4. **Makro / Insert Modu (OV02B10 + OV64B 2.5x Hibrit):** Yakın plan doku çekimleri.
5. **35mm Ön Selfie Kamerası (OmniVision OV16A1Q):** 21mm balıkgözü etkisini 35mm doğal portre oranına çeviren optik düzeltme + 2900K sıcak ekran reflektörü.

## GitHub Üzerinden Otomatik APK Üretimi
Bu depoyu GitHub'a yüklediğiniz anda `.github/workflows/build-apk.yml` otomatik çalışır ve derlenen **DuchampCine-PocoX6Pro.apk** dosyasını **Actions -> Artifacts** ve **Releases** sekmesine yükler.
