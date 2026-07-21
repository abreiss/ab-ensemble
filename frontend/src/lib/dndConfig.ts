import { PointerSensor, TouchSensor } from '@dnd-kit/core'
import type { PointerActivationConstraint } from '@dnd-kit/core'

/**
 * dnd-kit sensor configuration for the `/assemble` drag surface: a pointer
 * sensor (mouse/trackpad) and a touch sensor (touchscreen), each gated by a
 * small activation constraint so an ordinary tap, click, or scroll isn't
 * mistaken for a drag.
 *
 * ASSUMPTION A1.5 (deferred, non-blocking — see
 * `docs/specs/21-spec-manual-outfit-assembly/21-assumptions-manual-outfit-assembly.md`
 * and the spec's Open Question 4): this SDD run has no real iOS device to
 * tune against. The constants below are sensible defaults — a short pointer
 * distance threshold, and a touch hold-delay + movement tolerance chosen to
 * reduce scroll-vs-drag conflicts on a touchscreen — not device-verified
 * values. On-device tuning/validation of these thresholds is a documented,
 * deferred manual step (the on-device touch proof artifact), not a hard
 * blocker for this issue.
 */

/** Pixels the pointer must move before a mouse/trackpad drag activates. */
export const POINTER_ACTIVATION_CONSTRAINT: PointerActivationConstraint = { distance: 4 }

/**
 * Touch activation: a short hold (`delay`, ms) before the drag activates, and
 * how far the touch point may drift during that hold (`tolerance`, px) before
 * the pending drag is aborted in favor of an ordinary scroll.
 */
export const TOUCH_ACTIVATION_CONSTRAINT: PointerActivationConstraint = { delay: 150, tolerance: 8 }

/** Descriptor pairing the pointer sensor class with its activation options. */
export const pointerSensorConfig = {
  sensor: PointerSensor,
  options: { activationConstraint: POINTER_ACTIVATION_CONSTRAINT },
} as const

/** Descriptor pairing the touch sensor class with its activation options. */
export const touchSensorConfig = {
  sensor: TouchSensor,
  options: { activationConstraint: TOUCH_ACTIVATION_CONSTRAINT },
} as const

/**
 * Both descriptors together, for `useSensor(descriptor.sensor,
 * descriptor.options)` in `Assemble.tsx`. Kept as plain data (not a
 * `useSensors()` call) so this module stays hook-free and directly
 * unit-testable — `useSensor`/`useSensors` are React hooks and must be called
 * from the component itself.
 */
export const assembleSensorConfig = [pointerSensorConfig, touchSensorConfig] as const
