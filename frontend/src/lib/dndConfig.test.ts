import { PointerSensor, TouchSensor } from '@dnd-kit/core'
import { describe, expect, it } from 'vitest'

import {
  assembleSensorConfig,
  POINTER_ACTIVATION_CONSTRAINT,
  pointerSensorConfig,
  TOUCH_ACTIVATION_CONSTRAINT,
  touchSensorConfig,
} from './dndConfig'

describe('dndConfig', () => {
  it('configures a pointer sensor with a small activation distance', () => {
    expect(pointerSensorConfig.sensor).toBe(PointerSensor)
    expect(pointerSensorConfig.options.activationConstraint).toEqual({ distance: expect.any(Number) })
  })

  it('configures a touch sensor with a hold delay + movement tolerance', () => {
    expect(touchSensorConfig.sensor).toBe(TouchSensor)
    expect(touchSensorConfig.options.activationConstraint).toEqual({
      delay: expect.any(Number),
      tolerance: expect.any(Number),
    })
  })

  it('exposes both a pointer and a touch sensor descriptor for DndContext to consume', () => {
    expect(assembleSensorConfig).toHaveLength(2)
    expect(assembleSensorConfig.map((descriptor) => descriptor.sensor)).toEqual([PointerSensor, TouchSensor])
  })

  it('shares its activation constants as the single source of truth for the descriptors', () => {
    expect(pointerSensorConfig.options.activationConstraint).toBe(POINTER_ACTIVATION_CONSTRAINT)
    expect(touchSensorConfig.options.activationConstraint).toBe(TOUCH_ACTIVATION_CONSTRAINT)
  })
})
