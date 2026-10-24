// Núcleo de audio puro (Float32 mono en [-1, 1]). Mismo comportamiento que Pcm.kt de la app Android.

export const MAX_S = 10;               // tope de grabación
export const MIN_S = 0.15;             // menos que esto no alcanza a ser una palabra
export const TRIM_PAD_S = 0.05;        // margen que se deja antes y después de la voz
export const TAP_HEAD_S = 0.05;        // ruido al empezar a grabar
export const TAP_TAIL_S = 0.15;        // golpe del dedo al tocar para parar
export const TRIM_FLOOR = 300 / 32768; // piso del umbral de silencio (el real es un décimo del pico)
export const TARGET_PEAK = 29000 / 32768;
export const MAX_GAIN = 8;

const peakOf = (x) => {
  let p = 0;
  for (let i = 0; i < x.length; i++) { const a = Math.abs(x[i]); if (a > p) p = a; }
  return p;
};

export function reversed(x) {
  const out = new Float32Array(x.length);
  for (let i = 0; i < x.length; i++) out[i] = x[x.length - 1 - i];
  return out;
}

export function isPlayable(x, rate) {
  return x != null && x.length >= Math.round(MIN_S * rate);
}

export function withoutTapNoise(raw, rate, stoppedByTap) {
  const head = Math.round(TAP_HEAD_S * rate);
  const tail = stoppedByTap ? Math.round(TAP_TAIL_S * rate) : 0;
  if (raw.length <= head + tail) return new Float32Array(0);
  return raw.slice(head, raw.length - tail);
}

export function trimmedSilence(x, rate) {
  const threshold = Math.max(TRIM_FLOOR, peakOf(x) / 10);
  let first = -1, last = -1;
  for (let i = 0; i < x.length; i++) if (Math.abs(x[i]) > threshold) { first = i; break; }
  if (first < 0) return new Float32Array(0);
  for (let i = x.length - 1; i >= 0; i--) if (Math.abs(x[i]) > threshold) { last = i; break; }
  const pad = Math.round(TRIM_PAD_S * rate);
  return x.slice(Math.max(0, first - pad), Math.min(x.length, last + 1 + pad));
}

export function normalized(x) {
  const peak = peakOf(x);
  if (peak === 0) return x.slice();
  const gain = Math.min(TARGET_PEAK / peak, MAX_GAIN);
  if (gain <= 1) return x.slice();
  return x.map((s) => Math.max(-1, Math.min(1, s * gain)));
}

/** Lo que se guarda de cada grabación: sin silencios en las puntas y a buen volumen. */
export function cleaned(x, rate) {
  return normalized(trimmedSilence(x, rate));
}

/** WAV PCM 16-bit mono: Safari lo reproduce con <audio>, que suena aunque el iPhone esté en silencio. */
export function encodeWav(x, rate) {
  const buf = new ArrayBuffer(44 + x.length * 2);
  const v = new DataView(buf);
  const str = (o, s) => { for (let i = 0; i < 4; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  str(0, "RIFF"); v.setUint32(4, 36 + x.length * 2, true); str(8, "WAVE");
  str(12, "fmt "); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, "data"); v.setUint32(40, x.length * 2, true);
  for (let i = 0; i < x.length; i++) {
    const s = Math.max(-1, Math.min(1, x[i]));
    v.setInt16(44 + i * 2, s < 0 ? s * 32768 : s * 32767, true);
  }
  return buf;
}
