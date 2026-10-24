# App Mocha (audio al revés) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android para jugar a "palabra al revés": se graba una palabra, suena invertida, el otro la imita, su imitación se invierte y en esa cuarta salida se adivina la palabra.

**Architecture:** Una sola Activity con cuatro botones grandes (referencia: captura "Reverse Audio"). Audio en PCM crudo 16-bit mono 44.1 kHz: `AudioRecord` → `ShortArray` en memoria, `reverse()` puro, `AudioTrack` MODE_STATIC para reproducir. Sin MediaRecorder (AAC no se puede invertir sin decodificar).

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17 (Temurin portable), compileSdk/targetSdk 34, minSdk 24, Views nativas sin AndroidX. Tests: JUnit 4.13.2.

**Spec:** pedido del usuario (2026-10-23) + captura de referencia `Downloads\bb6241ba-...jfif`.

## Global Constraints

- Flujo de 4 botones: 1 Grabar original · 2 Escuchar al revés (original invertido) · 3 Grabar imitación · 4 Escuchar al revés (imitación invertida = la "cuarta salida").
- Pie visible: "Desarrollado por Ignacio Guacte" (regla global). README con línea de autoría.
- Sin banner de publicidad (el de la captura no se copia).
- Toolchain y caché de Gradle en `D:\Android` (C: tiene poco espacio).
- Entregable: APK debug firmado, minSdk 24, copiado a `D:\PROYECTOS_LICUAO\APP_MOCHA\dist\`.
- Textos en español chileno informal.

## Review Focus

- Grabación de largo cero (tocar y soltar al instante) → botón de reproducir queda deshabilitado, no crash.
- Reproducir antes de grabar → botones 2 y 4 deshabilitados hasta tener audio.
- Grabación larga → se corta sola a los 10 s.
- Grabar o reproducir mientras otra cosa suena → se detiene lo anterior primero.
- Permiso de micrófono denegado → mensaje claro, no crash; vuelve a pedirlo al tocar grabar.

---

### Task 1: Toolchain portable

**Files:** Create `build.ps1` (setea `JAVA_HOME`, `ANDROID_HOME=D:\Android\Sdk`, `GRADLE_USER_HOME=D:\Android\gradle-home`, llama `gradlew.bat`), `local.properties` (`sdk.dir=D:/Android/Sdk`).

- [ ] JDK 17 en `D:\Android\jdk17`, cmdline-tools en `D:\Android\Sdk\cmdline-tools\latest`, Gradle 8.7 en `D:\Android\gradle-8.7`.
- [ ] `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"` + `yes | sdkmanager --licenses` (Bash).
- [ ] `gradle wrapper --gradle-version 8.7` en el proyecto. Verificación: `.\build.ps1 --version` imprime Gradle 8.7 / JVM 17.

### Task 2: Núcleo de audio (TDD)

**Files:** Create `app/src/main/java/cl/guacte/mocha/audio/Pcm.kt`, Test `app/src/test/java/cl/guacte/mocha/audio/PcmTest.kt`.

**Interfaces — Produces:**
- `fun reversed(samples: ShortArray): ShortArray` (copia nueva, no muta la entrada)
- `fun isPlayable(samples: ShortArray?): Boolean` (true si hay ≥ 0,15 s = 6615 muestras)
- `const val SAMPLE_RATE = 44100`, `const val MAX_SAMPLES = SAMPLE_RATE * 10`

- [ ] Tests: `reversed(shortArrayOf(1,2,3)) == [3,2,1]`; vacío → vacío; la entrada no cambia; `reversed(reversed(x)) == x`; `isPlayable(null)==false`, `isPlayable(ShortArray(100))==false`, `isPlayable(ShortArray(6615))==true`.
- [ ] Correr `.\build.ps1 testDebugUnitTest` → FAIL; implementar; → PASS.

### Task 3: Grabador y reproductor

**Files:** Create `audio/Recorder.kt`, `audio/Player.kt`.

**Interfaces — Produces:**
- `class Recorder { fun start(onDone: (ShortArray) -> Unit); fun stop(); val isRecording: Boolean }` — hilo de lectura, corta en `MAX_SAMPLES`, `onDone` en hilo principal.
- `class Player { fun play(samples: ShortArray, onEnd: () -> Unit); fun stop() }` — `AudioTrack` MODE_STATIC, `setNotificationMarkerPosition` para `onEnd`; `stop()` libera.

### Task 4: Pantalla

**Files:** Create `MainActivity.kt`, `res/layout/activity_main.xml`, `res/values/{strings,colors,themes}.xml`, drawables de íconos (mic, play invertido, stop), `AndroidManifest.xml` (`RECORD_AUDIO`), ícono de app.

- [ ] Cuatro tarjetas a pantalla completa (peso 1 cada una), estado: idle / grabando (pulso + "Toca para parar") / sonando. Botones 2 y 4 deshabilitados sin `isPlayable`.
- [ ] Botón extra discreto "Revelar" que reproduce el original al derecho.
- [ ] `onStop` → `recorder.stop()`, `player.stop()`.
- [ ] Pie "Desarrollado por Ignacio Guacte".

### Task 5: APK

- [ ] `.\build.ps1 assembleDebug`; `apksigner verify` y `aapt2 dump badging` (package `cl.guacte.mocha`, minSdk 24, permiso RECORD_AUDIO).
- [ ] Copiar a `dist\Mocha.apk`. README con instrucciones de instalación.
