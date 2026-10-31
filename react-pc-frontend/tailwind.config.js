/** @type {import('tailwindcss').Config} */
export default {
    // Das Programm hat kein dunkles Design. Ohne diese Zeile schaltet Tailwind „dark:“-Klassen
    // nach der Einstellung des Betriebssystems um, und einzelne Bausteine (z. B. Aufklappmenüs
    // aus shadcn) wurden bei dunkel eingestelltem Mac schwarz. So greifen sie nur mit der
    // Klasse „dark“ am <html>, die das Programm nie setzt.
    darkMode: 'class',
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {},
    },
    plugins: [],
}
