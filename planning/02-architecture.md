# Ostomate 2.0 — Architecture

## Module Boundaries

| Module | Rule |
|---|---|
| `shared` | Domain + data only. **No Compose imports, ever.** Targets: androidMain, iosMain, commonMain, iosX64 (CI). |
| `composeApp` | All CMP UI, ViewModels, theme, `initKoin`. iOS: arm64 + simulatorArm64 only (CMP 1.11+ dropped iosX64). Builds `Shared.framework` for Xcode. |
| `androidApp` | Thin Android launcher: Application class, MainActivity, deep-link entry. |
| `iosApp` | Thin Xcode project: SwiftUI entry, `onOpenURL` deep-link bridge, OstomateWidget. |
| `build-logic` | Convention plugins (shared Gradle config). |

## Data Layer (`shared/data/`)

```
shared/data/
  db/
    OstomateDatabase.kt       ← Room KMP singleton; version exported to shared/schemas/
    ChangeEventDao.kt         ← insert, getAllEvents Flow, getRecentEvents, countByTypeSince
    ChangeEventEntity.kt      ← id, typeId (FK to supply_types), timestamp
    SupplyTypeDao.kt          ← CRUD for user-defined supply types
    SupplyTypeEntity.kt       ← id, name, colorHex, sortOrder
  settings/
    SettingsDataStore.kt      ← DataStore<Preferences> (platform-specific path via expect/actual)
    SettingsRepository.kt     ← wraps DataStore; exposes Flow<UserSettings>
  ChangeEventRepository.kt    ← records changes; enforces inventory floor at 0
  SupplyRepository.kt         ← supply type CRUD
  BackupRepository.kt         ← CSV round-trip backup/restore
```

**Room schema export:** ON — `shared/schemas/` holds every version's JSON. Every version bump ships a Migration + migration test. Never use `fallbackToDestructiveMigration`.

## Domain Layer (`shared/domain/`)

```
shared/domain/
  PredictionEngine.kt     ← getDaysRemaining, getAverageDaysBetweenChanges
  CalendarAggregator.kt   ← month → Map<Int, DayCount>
  DeepLinkParser.kt       ← ostomate://log?item=X → SupplyKind?
  NotificationScheduler.kt← schedules Notifier calls per-type when below threshold
  CsvExporter.kt          ← List<ChangeEvent> → CSV string
  SupplyKind.kt           ← sealed type for BAG / FLANGE / custom
```

## Platform Layer (`shared/platform/` — expect/actual)

| File | Android actual | iOS actual |
|---|---|---|
| `Notifier` | WorkManager + NotificationManager | UNUserNotificationCenter |
| `BiometricAuthenticator` | BiometricPrompt (BIOMETRIC_STRONG or DEVICE_CREDENTIAL) | LAContext (Face ID / Touch ID + passcode) |
| `CrashReporter` | Firebase Crashlytics (release only) | Firebase Crashlytics (release only) |
| `FeedbackHelper` | Google Play In-App Review | SKStoreReviewController |
| `FileSharer` | Intent.ACTION_SEND | UIActivityViewController |
| `Time` | System.currentTimeMillis() | NSDate |

## UI Layer (`composeApp/`)

```
composeApp/commonMain/
  ui/
    home/         HomeScreen + HomeViewModel
    calendar/     CalendarScreen + CalendarViewModel
    history/      HistoryScreen + HistoryViewModel
    stats/        StatsScreen + StatsViewModel
    onboarding/   OnboardingScreen + OnboardingViewModel
    settings/
      SettingsScreen + SettingsViewModel
      ManageSuppliesScreen + ManageSuppliesViewModel
      QrLabelsScreen
      ReorderWarningsScreen
      PrivacyPolicyScreen
    components/   LogButton, Pill, SupplyCard, WarningBanner
    theme/        OstomateColors, Theme, Typography
  App.kt          ← NavHost + bottom nav setup
  di/AppKoin.kt   ← initKoin() wires shared + UI modules
```

## UDF Pattern (enforced)

```
UI → ViewModel (one UiState per screen via StateFlow, UDF) → UseCase / Domain → Repository → Room / DataStore
```

- ViewModels expose a single `StateFlow<UiState>` (sealed class or data class)
- Side effects go through a `SharedFlow<UiEvent>` (one-shot toasts, navigation)
- No business logic in Composables — only UI state observation + event dispatch
- No `!!`; platform code via expect/actual only

## DI (Koin)

- `shared/di/Koin.kt` — data + domain modules
- `composeApp/di/AppKoin.kt` — UI modules; calls `initKoin()` at app start
- Platform-specific bindings (e.g., `Notifier(context)` on Android) provided in platform modules
- Never instantiate repositories or DAOs directly outside Koin

## Deep Link Flow

```
Camera / QR reader fires ostomate://log?item=bag
  → Android: MainActivity.onNewIntent / onCreate
  → iOS: iOSApp.onOpenURL
  → DeepLinkBus.emit(uri)
  → HomeViewModel observes DeepLinkBus
  → DeepLinkParser.parse(uri) → SupplyKind?
  → ChangeEventRepository.recordChange(kind)
  → Toast confirmation
```
