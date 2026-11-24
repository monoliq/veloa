# Veloa

**Veloa** — Android üçün 100% Kotlin və Jetpack Compose (Material 3) ilə hazırlanmış minimalist, sürətli və reklamsız jest / yan panel köməkçisi.  
*Minimalist, fast, and ad-free gesture & side-panel launcher for Android, built with 100% Kotlin and Jetpack Compose (Material 3).*

---

## Xüsusiyyətlər / Features

- **Yan Panel və Üzən Düymə / Side Panel & Floating Button**: Ekranın kənarından sürüşdürərək (*Birbaşa çək* və ya *Əvvəl aç, sonra çək*) və ya üzən dairəvi düymə ilə jest çəkin.
- **Oriyentasiyaya sadiq jest mühərriki / Orientation-Faithful Gesture Engine**: Saf Kotlin izotropik tanıma mühərriki (`GestureRecognizer`) — şaquli düz xətt (`|`), `L`, `C`, `V` və çox-xətli jestlər həm portret, həm də üfüqi rejimdə eyni dəqiqliklə tanınır.
- **1-ə-1 Həssaslıq / Exact Sensitivity Control**: 50%–95% aralığında birbaşa uyğunluq həddi və bucaq tolerantlığı.
- **Fasiləsiz arxa fon və bank uyğunluğu / Resilient Background & Banking Mode**: `VeloaKeepAliveService`, `VeloaWatchdogJobService` və `SYSTEM_ALERT_WINDOW` dəstəyi ilə hətta Əlçatanlıq deaktiv olduqda belə tətbiq açma jestləri işləyir.
- **3 Tema × 6 Rəng Palitrası / 3 Themes × 6 Color Palettes**: İşıqlı, Qaranlıq, Tam Qara (AMOLED) + Okean, Zümrüd, Bənövşəyi, Mərcan, Kəhrəba, Monoxrom.
- **11 Dil Dəstəyi / 11 Languages**: Alman, Azərbaycan, Fransız, İngilis, İspan, İtalyan, Polyak, Portuqal, Rus, Türk, Ukrayna.

---

## Qurma / Build

1. Android Studio və ya JDK 17 + Android SDK (compileSdk 36, minSdk 26) quraşdırın.
2. Debug APK yaratmaq üçün:
   ```bash
   ./gradlew assembleDebug
   ```
3. Vahid testləri işlətmək üçün:
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. İmzalı Release APK üçün kök qovluqda `keystore.properties` yaradın:
   ```properties
   storeFile=../release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=YOUR_ALIAS
   keyPassword=YOUR_KEY_PASSWORD
   ```
   Sonra `./gradlew assembleRelease` əmrini icra edin.
