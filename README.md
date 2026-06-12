# PettyCash 💸 — Office Petty Cash Manager

A lightweight, single-file web app to manage office petty cash. No installation, no server, no database — just open `index.html` in any modern browser.

## Features

- **Dashboard** — cash-in-hand balance with configurable low-balance alert, monthly spend vs top-ups chart, category breakdown, top spend areas, and recent activity
- **Transactions** — record expenses (₹, category, paid-by, receipt status/number) and cash top-ups; search and filter by type, category, month, or receipt status
- **Receipt tracking** — flag expenses with pending receipts and mark them received with one click
- **Excel/CSV** — import your existing Excel data (save as CSV) and export everything back with a running-balance column
- **Backup/Restore** — full JSON backup including settings, categories, and team members

## Getting started

1. Open `index.html` in a browser (double-click it), or
2. Host it with GitHub Pages: **Settings → Pages → Source: main branch** — then open `https://YOUR_USERNAME.github.io/petty-cash-manager/`

## How data is stored

All data is saved in the browser's `localStorage` — it stays on the machine and browser where it was entered. Use **Settings → Backup & Restore** to move data between machines, and export backups regularly.

## Importing your existing Excel sheet

Save your sheet as CSV (File → Save As → CSV) and use **Import CSV** on the Transactions page. Columns are auto-detected; at minimum you need **Date** and **Amount**. Dates in `DD/MM/YYYY` or `YYYY-MM-DD` both work.

## Tech

Single HTML file — vanilla JavaScript, CSS, and [Chart.js](https://www.chartjs.org/) (loaded from CDN).
