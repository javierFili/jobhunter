import { useCallback, useState } from 'react'

export function useSelection<K extends string | number>() {
  const [selected, setSelected] = useState<K[]>([])
  const toggle = useCallback(
    (id: K, on: boolean) => setSelected((prev) => (on ? [...prev.filter((p) => p !== id), id] : prev.filter((p) => p !== id))),
    [],
  )
  const clear = useCallback(() => setSelected([]), [])
  return { selected, setSelected, toggle, clear, isSelected: (id: K) => selected.includes(id) }
}
