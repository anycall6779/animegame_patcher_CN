# CHINA_PATCH_ANDROID

중국판 공식 APK를 Android 기기에서 직접 다운로드하거나 수동 선택하고, 번역 JS를 받아 패치한 뒤 서명된 APK로 출력하는 패처 앱이다.

## 동작 순서

1. `https://www.benghuai.com/download/config`에서 서버별 APK 목록을 가져온다.
2. 사용자가 서버를 선택한다.
3. 선택한 서버의 `android_download_url` APK를 다운로드하거나 사용자가 선택한 APK를 사용한다.
4. 기본 Codeberg JS 또는 사용자가 선택한 JS를 번역 blob으로 변환한다.
5. APK를 패치한다.
   - `AndroidManifest.xml`의 Application class를 `ComboAppProxy`로 변경
   - `classes5.dex` 삽입
   - `lib/arm64-v8a/libanimegame_native_localify.so` 삽입
   - `assets/animegame_translation_blob.bin` 삽입
   - 앱 표시 이름을 `붕괴학원2` 또는 `崩坏学园2`로 선택 적용
   - 아이콘을 `ic_launcher.png` 또는 `app_icon.png`로 선택 적용
   - 기존 `META-INF/` 서명 제거
   - `resources.arsc`/`.so` 정렬 유지
6. `auto-singer-main/app_signer.keystore`와 같은 키 값으로 V2/V3 서명한다.
7. 결과 APK를 앱 외부 파일 저장소에 저장한다.

## 앱 기능

- 공식 서버 APK 목록 자동 조회
- 수동 APK 선택
- 기본 번역 JS 다운로드
- 수동 JS 선택
- 다운로드/복사/패치/서명 진행률 표시
- 다운로드/복사 속도 표시
- 예상 남은 시간 표시
- 결과 APK 이름 선택: `붕괴학원2`, `崩坏学园2`
- 결과 APK 아이콘 선택: `ic_launcher.png (카린)`, `app_icon.png (키아나)`
- 아이콘 미리보기
- 라이트/다크 모드 전환
- 결과 APK 설치 화면 열기
- 다운로드 스트림이 중간에 끊길 때 3회 재시도
- 원본 APK의 `classes*.dex`를 보존하고 다음 빈 dex 번호에 로더를 추가한다. 예: bilibili판은 원본 `classes5.dex`/`classes6.dex`가 있으므로 로더가 `classes7.dex`로 들어간다.

기본 번역 JS:

```text
https://codeberg.org/rouzzang/animagameGirlZ/raw/commit/ca7d11156f1140271a80e39b7d45af8985af28bc/libfrida-gadget.script_KR.js
```

## 포함된 payload

현재 앱 assets에는 다음 파일이 들어 있다.

```text
app/src/main/assets/payload/classes5.dex
app/src/main/assets/payload/libanimegame_native_localify.so
app/src/main/assets/payload/ic_launcher.png
app/src/main/assets/payload/app_icon.png
app/src/main/assets/signing/app_signer.keystore
```

payload를 바꾸려면 PC에서 `CHINA_PATCH` payload를 다시 빌드한 뒤 위 assets 파일을 교체하고 앱을 다시 빌드하면 된다. 현재 native payload는 APK 내부의 `assets/animegame_translation_blob.bin`을 우선 읽고, 없으면 `.so`에 내장된 fallback 번역을 사용한다.

## 빌드

```powershell
CHINA_PATCH_ANDROID\.gradle-dist\gradle-8.14.3\bin\gradle.bat -p CHINA_PATCH_ANDROID :app:assembleDebug --no-daemon
```

빌드 결과:

```text
CHINA_PATCH_ANDROID/app/build/outputs/apk/debug/app-debug.apk
```

## 주의

- Android 앱 내부에서는 Windows용 `zipalign.exe`나 `apksigner.jar`를 직접 실행하지 않는다.
- 대신 같은 keystore 값을 assets에 넣고 `com.android.tools.build:apksig`로 서명한다.
- 이 앱에 포함된 keystore는 APK에서 추출 가능하다. 공개 배포용 보안 키로 쓰면 안 된다.
- 공식 앱과 서명이 다르므로 기존 공식 앱 위에 덮어쓰기 설치는 불가능하다. 기존 앱을 삭제한 뒤 설치해야 한다.
