# Mocha · palabra al revés

Juego para dos personas en un mismo celular Android.

1. **Grabar palabra**: una persona graba una palabra sin que la otra sepa cuál es.
2. **Escuchar al revés**: suena dada vuelta. La otra persona la escucha (las veces que quiera).
3. **Grabar imitación**: la otra persona imita ese sonido al revés, tal cual lo escuchó.
4. **¿Qué palabra es?**: su imitación se vuelve a dar vuelta, y ahí debería aparecer la palabra. Hay que adivinarla.

**Revelar** reproduce la palabra original al derecho. El marcador **J1 / J2** suma un punto al tocarlo y se
reinicia si lo mantienes apretado. **Nueva ronda** (arriba a la derecha) borra las grabaciones.

## iPhone (web app)

En `web/` está la misma app como página web. Se publica en GitHub Pages (rama `gh-pages`, solo el contenido
de `web/`). En el iPhone: abrir el link en **Safari** → botón Compartir → **Agregar a inicio**. Queda como app
con ícono propio. La primera vez pide permiso para el micrófono.

Tests del audio web: `node --test web/pcm.test.mjs`. Publicar cambios: `git subtree push --prefix web origin gh-pages`.

## Instalar en Android

APK listo en `dist/Mocha.apk` (Android 7.0 o superior). Para pasárselo a alguien: mandarlo por WhatsApp,
Drive o cable, abrirlo en el celular y permitir "Instalar apps desconocidas" para la app desde la que se abre.
Play Protect puede avisar que la app es de un desarrollador desconocido: tocar "Instalar de todas formas".

## Compilar

Toolchain portable en `D:\Android` (JDK 17, Android SDK 34, Gradle 8.7). No hace falta Android Studio.

```powershell
.\build.ps1 testDebugUnitTest   # tests del núcleo de audio
.\build.ps1 assembleDebug       # APK en app\build\outputs\apk\debug\
```

## Cómo funciona

Graba PCM 16-bit mono a 44,1 kHz con `AudioRecord` (tope 10 s), recorta el silencio de las puntas,
normaliza el volumen y lo reproduce invertido con `AudioTrack`. Todo en memoria: no guarda archivos ni usa
internet. La lógica de audio está en `app/src/main/java/cl/guacte/mocha/audio/Pcm.kt` y tiene tests.

---

Desarrollado por Ignacio Guacte
