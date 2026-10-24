// Tests del núcleo de audio web. Correr con: node --test web/
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  reversed, isPlayable, trimmedSilence, normalized, withoutTapNoise, cleaned, encodeWav,
  TRIM_PAD_S, TAP_HEAD_S, TAP_TAIL_S, TARGET_PEAK, MAX_GAIN, MIN_S,
} from "./pcm.js";

const RATE = 48000;
const fill = (n, v) => new Float32Array(n).fill(v);
const cat = (...parts) => {
  const out = new Float32Array(parts.reduce((s, p) => s + p.length, 0));
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
};
const pad = Math.round(TRIM_PAD_S * RATE);

test("reversed invierte sin modificar la entrada", () => {
  const input = Float32Array.from([0.1, 0.2, 0.3]);
  assert.deepEqual(Array.from(reversed(input)), Array.from(Float32Array.from([0.3, 0.2, 0.1])));
  assert.deepEqual(Array.from(input), Array.from(Float32Array.from([0.1, 0.2, 0.3])));
  assert.equal(reversed(new Float32Array(0)).length, 0);
});

test("isPlayable exige al menos 0,15 s", () => {
  assert.equal(isPlayable(null, RATE), false);
  assert.equal(isPlayable(new Float32Array(100), RATE), false);
  assert.equal(isPlayable(new Float32Array(Math.round(MIN_S * RATE)), RATE), true);
});

test("trimmedSilence quita silencio de las puntas dejando margen", () => {
  const out = trimmedSilence(cat(fill(20000, 0), fill(10000, 0.15), fill(20000, 0)), RATE);
  assert.equal(out.length, 10000 + 2 * pad);
  assert.equal(out[0], 0);
  assert.ok(Math.abs(out[pad] - 0.15) < 1e-6);
});

test("trimmedSilence: solo ruido bajo queda vacío", () => {
  assert.equal(trimmedSilence(fill(20000, 0.006), RATE).length, 0);
});

test("trimmedSilence: voz baja no se pierde (umbral relativo)", () => {
  const out = trimmedSilence(cat(fill(20000, 0.001), fill(10000, 0.02), fill(20000, 0.001)), RATE);
  assert.equal(out.length, 10000 + 2 * pad);
});

test("normalized sube el pico al objetivo, con tope y sin bajar volumen", () => {
  const out = normalized(Float32Array.from([0, 0.1, -0.2]));
  assert.ok(Math.abs(-out[2] - TARGET_PEAK) < 1e-6);
  assert.ok(Math.abs(out[1] - TARGET_PEAK / 2) < 1e-6);
  assert.ok(Math.abs(normalized(Float32Array.from([0.01]))[0] - 0.01 * MAX_GAIN) < 1e-6);
  assert.deepEqual(Array.from(normalized(Float32Array.from([0.98, -0.98]))), Array.from(Float32Array.from([0.98, -0.98])));
  assert.deepEqual(Array.from(normalized(new Float32Array(3))), [0, 0, 0]);
});

test("withoutTapNoise: el golpe del dedo al parar no sobrevive", () => {
  const head = Math.round(TAP_HEAD_S * RATE);
  const tail = Math.round(TAP_TAIL_S * RATE);
  const toque = cat(fill(500, 0.6), fill(tail - 500, 0));
  const raw = cat(fill(head, 0.6), fill(10000, 0), fill(20000, 0.15), fill(RATE, 0), toque);
  const out = cleaned(withoutTapNoise(raw, RATE, true), RATE);
  assert.equal(out.length, 20000 + 2 * pad);
});

test("withoutTapNoise: si paró por tiempo no corta la cola; si es muy corta queda vacía", () => {
  const head = Math.round(TAP_HEAD_S * RATE);
  assert.equal(withoutTapNoise(fill(RATE, 0.1), RATE, false).length, RATE - head);
  assert.equal(withoutTapNoise(fill(head, 0.1), RATE, true).length, 0);
});

test("encodeWav arma un WAV PCM 16-bit mono válido", () => {
  const buf = encodeWav(Float32Array.from([0, 1, -1]), RATE);
  const v = new DataView(buf);
  const tag = (o) => String.fromCharCode(v.getUint8(o), v.getUint8(o + 1), v.getUint8(o + 2), v.getUint8(o + 3));
  assert.equal(tag(0), "RIFF");
  assert.equal(tag(8), "WAVE");
  assert.equal(v.getUint16(22, true), 1); // mono
  assert.equal(v.getUint32(24, true), RATE);
  assert.equal(v.getUint16(34, true), 16);
  assert.equal(v.getUint32(40, true), 6); // 3 muestras * 2 bytes
  assert.equal(buf.byteLength, 44 + 6);
  assert.equal(v.getInt16(46, true), 32767);
  assert.equal(v.getInt16(48, true), -32768);
});
