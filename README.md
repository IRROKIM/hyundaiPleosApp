# Route Soundtrack

현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 출품작.

**목적지를 정하면 경로 위에 재생 지점을 계획하고, 그 지점에서만 주행 상황에 맞는 90–120초 로우파이 곡을 한 번 틀어 주는 Pleos Connect 앱입니다.** 음악을 계속 트는 대신 "필요한 구간에만" 틀고, 그 구간을 경로 위에 보여 줍니다.

> 1차 심사용 컨셉 버전(v0.1.0-concept)입니다. **Pleos Connect SDK**(NaviHelper 3.0.5 · Vehicle 2.3.2)로 내비 경로와 차량 상태를 읽어 동작합니다.

## 화면·소리에서 보이는 것

| # | 컨셉 | 화면·소리에서 확인하는 방법 |
|---|---|---|
| 1 | 재생 지점을 경로 위에 시각화 | 내비에서 목적지를 정하면 앱의 경로 스트립에 ♪ 마커가 생김 |
| 2 | 정해진 지점에서만 짧은 곡 재생, 나머지는 무음 | 5 km 지점에서 곡 시작, 끝나면 "무음" |
| 3 | 상황별 음악과 각성 단계 | 시내·정체 = 로우파이 chill. 장거리 프리셋의 고속도로 = **각성형(드라이브 신스웨이브)**. 연속 주행 90분 이상 = **파워(하우스·EDM)**. 곡 성격 칩으로 직접 고를 수도 있다 |
| 4 | 고속도로 정책 | 여행 프리셋: 고속 구간 큐는 "곡 대기" → 고속도로를 벗어나면 재생 |
| 5 | 안내 음성 우선 | 내비 안내 음성이 오디오 포커스를 가져가면 음악 30%로 감소 → 끝나면 복귀 |
| 6 | 도착 곡 | 도착 8분 전(내비 ETA) 마무리 곡, 도착하면 페이드아웃 |
| 7 | 설정에 따라 계획이 바뀜 / 주행 중 잠금 | 프리셋·밀도를 바꾸면 ♪ 마커가 즉시 다시 그려짐. 사운드트랙이 켜져 있거나 주행 규제 중이면 잠김 |

## 사용하는 Pleos SDK

| SDK | 권한 | 읽는 값 |
|---|---|---|
| NaviHelper | `pleos.car.permission.NAVI_ROUTE` | 안내 상태(`naviMode`), 목적지 이름, 남은 거리(m)·남은 시간(초) (`DestinationInfo`, `DrivingInfo`) |
| Vehicle | `pleos.car.permission.CAR_DRIVE` | 트립 주행 거리·시간, 브레이크 페달, 주행 모드 |
| Vehicle | `pleos.car.permission.CAR_LIGHTS` | 하향등·미등 (야간 판정) |

SDK는 Pleos 공개 Maven 저장소(`https://nexus-playground.pleos.ai/repository/maven-releases/`)에서 받습니다. 인증은 필요 없습니다. SDK에는 속도 값이 없어서 최근 30초 동안의 진행 거리로 속도를 추정합니다.

## 특징

- **음원 파일이 없습니다.** 앱 안에서 수학적으로 합성해 큐마다 다른 곡을 만들므로 라이선스 문제가 없습니다.
  - 로우파이: `audio/StemSynth.kt`
  - 신스웨이브·하우스: `audio/ElectronicRenderer.kt`
  - 각성형·파워 곡은 빌드업→메인→브레이크→메인으로 에너지가 오르내립니다.
  - 미리 듣기: `RS_PREVIEW=1 ./gradlew testDebugUnitTest --tests "*TrackPreviewExport*"`를 실행하면 `app/build/preview/*.wav`가 생깁니다.
- **재생 판단은 한 곳에서.** 순수 Kotlin 상태 머신 `TriggerEngine`이 결정하고, 오디오와 화면은 결과만 따릅니다. JVM 단위 테스트로 검증합니다.
- **Pleos UI 가이드 반영.** 앱 창 크기에 따라 가로·세로 레이아웃이 바뀌고, 핵심 정보 하나를 크게 보여 줍니다. 주행 중에는 애니메이션이 없고 조작을 잠급니다. 주간·야간 모드와 한국어·영어를 지원합니다.
- **서버·DB 없음.** 설정 3개만 기기 안에 저장합니다.

## 빌드

| 항목 | 버전 |
|---|---|
| JDK | **17** (Gradle 8.9가 실행되는 버전) |
| Gradle | 8.9 (wrapper 포함) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.21, Jetpack Compose |
| compileSdk / targetSdk | 34 (없으면 Gradle이 자동으로 내려받음) |
| minSdk | **28** (Pleos 필수 요건) |

```bash
# JAVA_HOME = JDK 17
./gradlew testDebugUnitTest assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Android Studio에서 열 때는 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**를 17로 지정합니다. AGP 업그레이드 제안은 건너뜁니다.

릴리스 서명은 저장소 밖 keystore를 가리키는 `keystore.properties`(커밋 금지)로 합니다.

```properties
storeFile=C:/keys/route-soundtrack.jks
storePassword=...
keyAlias=route-soundtrack
keyPassword=...
```

## 실행 방법 (Pleos Connect Emulator)

1. Pleos Connect Emulator 2.1.0을 **cold boot**로 켜고 APK를 설치합니다.
2. 앱에서 주행 목적·음악 밀도·곡 성격을 고릅니다.
3. 내비게이션에서 목적지를 정하고 **안내 시작**을 누릅니다. 앱으로 돌아오면 헤더에 목적지·남은 거리가 나오고, 경로 스트립에 재생 지점이 그려집니다.
4. **사운드트랙 켜기**를 누릅니다. "안내 시작 시 자동 시작"을 켜 두면 경로 안내가 시작될 때 스스로 켜집니다(앱 화면이 보이는 상태에서).
5. 켜 두면 포그라운드 서비스로 돌아서 내비 화면으로 바꿔도 이어집니다. 차량 값(브레이크·조명)은 Virtual Console(`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088)에서 바꿀 수 있습니다.

참고: 차가 경로를 따라 움직여야 진행 거리가 늘어납니다. 차가 서 있으면 재생 지점까지 남은 거리는 그대로이고, 도착 8분 전 조건(남은 시간 ≤ 8분)인 짧은 경로에서는 바로 도착 곡이 나옵니다.

### 디버그 빌드: 내장 시나리오

에뮬레이터 없이 화면·소리를 확인할 때는 `app/src/debug/java/com/routesoundtrack/data/DriveInputs.kt`의 `USE_SIMULATOR`를 `true`로 바꿉니다. 내장 시나리오("시내 → 고속도로 → 정체" 50 km, `app/src/debug/assets/scenarios/`)를 곡이 없는 구간은 20배속, 곡이 나오는 동안은 실시간으로 달립니다. 릴리스 빌드에는 시나리오 코드와 파일이 들어가지 않습니다.

## 구조

```text
app/src/main/java/com/routesoundtrack/
├─ domain/    재생 판단 (순수 Kotlin): SegmentPlanner, ContextClassifier, Policy, TriggerEngine
├─ audio/     StemSynth·ElectronicRenderer(합성) → TrackComposer(곡 구성) → StemMixer(AudioTrack), AudioFocusController, SoundtrackService(MediaSession)
├─ data/      DriveInput(입력 인터페이스), SnapshotAssembler(값 조립·속도 추정), SettingsStore
│  └─ sdk/    PleosNaviSource(NaviHelper), PleosVehicleSource(Vehicle), SdkDriveInput, UxRestrictionSource
├─ session/   DriveSession: 입력 → 엔진 → 오디오·화면 연결, RouteWatcher(자동 시작)
└─ ui/        Compose 화면, theme/ (색·글꼴·모양 토큰)
app/src/debug/    ScenarioDriveInput·ScenarioSimulator + 시나리오 JSON (디버그 빌드 전용)
app/src/release/  DriveInputs: 항상 SDK
```

## 2차 계획

- 재탐색 시 남은 재생 지점 다시 계획, 경유지 큐
- 도로 등급(제한 속도 `onCurrentRoadSpeedLimit`) 기반 고속도로 판정
- 주행 로그 카드(기기 내 Room)
- 비·야간 반응, 동승자 기반 가족 프리셋 추천, VIN 기반 차종 추천
- UI 디자인 컨셉 적용(`ui/theme/`)
