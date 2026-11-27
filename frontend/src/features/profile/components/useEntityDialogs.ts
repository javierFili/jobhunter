import { useState } from 'react'

/** State for "create/edit" dialog + "confirm delete" dialog of a list of entities. */
export function useEntityDialogs<T>() {
  const [editing, setEditing] = useState<T | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [deleting, setDeleting] = useState<T | null>(null)
  return {
    editing,
    formOpen,
    deleting,
    openCreate: () => {
      setEditing(null)
      setFormOpen(true)
    },
    openEdit: (item: T) => {
      setEditing(item)
      setFormOpen(true)
    },
    setFormOpen,
    askDelete: setDeleting,
    closeDelete: () => setDeleting(null),
  }
}
