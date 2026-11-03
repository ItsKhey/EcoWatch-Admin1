# Module layout

The project is split into Gradle modules nested under `app/`. `:app` is the only
installable module; everything else is an Android library.

```
app/
├── src/main/                     :app            application shell
│   ├── AndroidManifest.xml                       manifest + component wiring
│   └── java/.../MyEcoWatchApp.java               Application, DI-by-hand
├── core/                         :app:core       resources, BuildConfig, leaf utils
├── data/                         :app:data       models, Room, Retrofit, repositories
├── ui-common/                    :app:ui-common  screens shared by 2+ features
├── notifications/                :app:notifications  FCM service + WorkManager retry
└── feature/
    ├── alerts/       :app:feature:alerts
    ├── analytics/    :app:feature:analytics
    ├── announcements/ :app:feature:announcements
    ├── auth/         :app:feature:auth
    ├── clusters/     :app:feature:clusters
    ├── home/         :app:feature:home
    ├── personnel/    :app:feature:personnel
    ├── profile/      :app:feature:profile
    ├── reports/      :app:feature:reports
    └── residents/    :app:feature:residents
```

## Dependency direction

```
:app
 ├── :app:notifications ──┐
 ├── :app:ui-common ──────┤
 └── :app:feature:* ──────┴──> :app:data ──> :app:core
```

Rules that keep the graph acyclic:

- `:app:core` depends on no project module. All `res/` live here, so
  `com.example.ecowatchadmin1.R` and `com.example.ecowatchadmin1.BuildConfig`
  are owned by this module. Other modules import that `R` explicitly.
- `:app:data` may not reference any `ui.*` class. System notifications receive
  their destination through `NotificationIntentFactory`, installed by
  `MyEcoWatchApp`.
- `:app:ui-common` holds the report detail dialog/adapters, reporter account
  rows and the `DashboardNavigator` contract because they are used by several
  features. It depends on `:app:data` only.
- Feature modules may depend on `:app:ui-common` and on other feature modules
  when the dependency is genuinely one-way (for example profile → auth). They
  never depend on `:app`.
- `DashboardActivity` lives in `:app`. Features talk to it through
  `DashboardNavigator` and launch it through `AppNavigation`, so no feature
  needs a compile-time reference to the application module.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Unit tests remain in `:app/src/test`; the application module depends on every
other module, so the tests can reach all of them. Room's `annotationProcessor`
is declared in `:app:data`, which owns the database.
