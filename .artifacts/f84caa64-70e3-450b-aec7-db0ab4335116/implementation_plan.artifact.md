# Fix ClassNotFoundException for MyEcoWatchApp

The application is failing to start with a `ClassNotFoundException` for `com.example.ecowatchadmin1.MyEcoWatchApp`. This usually happens when there is a mismatch between the `AndroidManifest.xml` declaration and the actual class package, or when the build configuration prevents the class from being included in the DEX files.

## Proposed Changes

### Build Configuration

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/EcoWatch Admin1/app/build.gradle.kts)
- Standardize the `compileSdk` and `targetSdk` values. The current `release(37)` syntax is non-standard and may be causing build inconsistencies.
- Ensure the `namespace` and `applicationId` are correctly configured.

### Manifest

#### [MODIFY] [app/src/main/AndroidManifest.xml](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/EcoWatch Admin1/app/src/main/AndroidManifest.xml)
- Change `android:name` for the application to the relative form `.MyEcoWatchApp`. This is more robust against package name discrepancies.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug` to ensure the project still builds.
- Run `:app:testDebugUnitTest` to verify that the `Application` class is still accessible.

### Manual Verification
- The user should attempt to run the application again and verify that the `FATAL EXCEPTION: main` with `ClassNotFoundException` is resolved.
