import { useState, type KeyboardEvent } from 'react'

interface DescriptorChipsProps {
  value: string[]
  onChange: (next: string[]) => void
}

/**
 * Add/remove chip editor for an item's free-form `descriptors`. Renders each
 * descriptor as a removable chip and offers a small input to add new ones. It
 * uses no `<form>` element so it can be embedded inside the parent `TagForm`
 * without nesting forms; adding is wired to both the button and the Enter key.
 */
export default function DescriptorChips({ value, onChange }: DescriptorChipsProps) {
  const [draft, setDraft] = useState('')

  const add = () => {
    const next = draft.trim()
    if (next === '' || value.includes(next)) {
      return
    }
    onChange([...value, next])
    setDraft('')
  }

  const remove = (descriptor: string) => {
    onChange(value.filter((d) => d !== descriptor))
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      add()
    }
  }

  return (
    <div className="chips-field">
      {value.length > 0 && (
        <ul className="chips">
          {value.map((descriptor) => (
            <li key={descriptor} className="chip">
              <span className="chip-label">{descriptor}</span>
              <button
                type="button"
                className="chip-remove"
                aria-label={`Remove ${descriptor}`}
                onClick={() => remove(descriptor)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="chip-add">
        <input
          type="text"
          className="input"
          aria-label="Add a descriptor"
          placeholder="Add a descriptor"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <button type="button" className="btn" onClick={add}>
          Add
        </button>
      </div>
    </div>
  )
}
