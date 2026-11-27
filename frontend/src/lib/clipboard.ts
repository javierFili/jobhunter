/** Copies text to the clipboard (throws if the browser denies it). */
export async function copyToClipboard(text: string): Promise<void> {
  if (!navigator.clipboard) throw new Error('Clipboard API no disponible')
  await navigator.clipboard.writeText(text)
}
