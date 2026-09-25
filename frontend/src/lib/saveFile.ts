/**
 * Hands a downloaded file to the browser as a save, under the given name.
 *
 * Kept apart from the page that uses it because object URLs and synthetic clicks do not exist in
 * the test DOM; tests replace this one function instead of patching globals.
 */
export function saveFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
