/** Textarea "one per line" ↔ string[] */
export const linesToList = (text: string): string[] =>
  text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)

export const listToLines = (list: string[] | null | undefined): string => (list ?? []).join('\n')

/** True when both dates are set and end < start. */
export const endBeforeStart = (start: string, end: string): boolean => !!start && !!end && end < start
