# Integrate Supabase Kotlin Client into FocusTag

This plan details the integration of the Supabase Kotlin client for authentication into the existing FocusTag Android application.

## User Review Required

> [!IMPORTANT]
> - `local.properties` must contain `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`.
> - The application will require Internet permission.
> - `BuildConfig` will be enabled to securely expose Supabase credentials to the Kotlin code.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///Users/mdyusuffatah/Desktop/FocusTag/gradle/libs.versions.toml)
Add versions and library definitions for Supabase (BOM and Auth), Ktor (Android engine), and Kotlin Serialization.

#### [MODIFY] [build.gradle.kts (root)](file:///Users/mdyusuffatah/Desktop/FocusTag/build.gradle.kts)
Apply the Kotlin Serialization plugin.

#### [MODIFY] [build.gradle.kts (:app)](file:///Users/mdyusuffatah/Desktop/FocusTag/app/build.gradle.kts)
- Enable `buildConfig`.
- Extract `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from `local.properties` and add them to `buildConfigField`.
- Add Supabase, Ktor, and Serialization dependencies.
- Apply the Kotlin Serialization plugin.

### Manifest and Network

#### [MODIFY] [AndroidManifest.xml](file:///Users/mdyusuffatah/Desktop/FocusTag/app/src/main/AndroidManifest.xml)
Add the `android.permission.INTERNET` permission.

### Data Layer

#### [NEW] [SupabaseClient.kt](file:///Users/mdyusuffatah/Desktop/FocusTag/app/src/main/java/com/focustag/app/data/supabase/SupabaseClient.kt)
Create a provider for the `SupabaseClient` initialized with the `Auth` module and credentials from `BuildConfig`.

## Verification Plan

### Automated Tests
- Run `./gradlew build` to ensure the project compiles and syncs correctly.
- Verify `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_PUBLISHABLE_KEY` are generated correctly.

### Manual Verification
- Verify that the `SupabaseClient` can be instantiated without crashing in a simple test or log statement (optional, since UI is not yet required).
