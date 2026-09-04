# Mobile PWA — UI Kit

A pixel-accurate recreation of **react-zeiterfassung** at the dashboard screen.

## What's modeled

- **Header**: weekday + date on one line, big "Hallo, Max Mustermann!" greeting below, right-aligned online pill (green dot + "Online" in soft green).
- **Hero CTA**: solid rose-600 rounded-2xl card, white Play icon in a translucent square tile, "Zeit erfassen" + "Neue Buchung starten", chevron-right on far right, rose glow shadow.
- **2×2 grid** of big icon cards: Projekte (rose), Angebote (amber), Kunden (indigo), Lieferanten (green).
- **Action list rows**: Kalender (indigo), Abwesenheit beantragen (rose), Saldenauswertung (green) — white rounded-xl cards with a colored icon tile left and title/subtitle.
- **Footer tile**: "Heute gearbeitet" with clock icon + big tabular-nums total (0h 00min).

Press feedback uses `transform: scale(.97)` on the primary button (per `react-zeiterfassung/src/index.css`).

## Files

- `index.html` — entry, renders inside an `ios_frame` starter for device-accurate framing
- `mobile.jsx` — the full mobile screen as one component tree
