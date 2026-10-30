# MediCare PMS — UI Design System

Inspired by professional pharmacy dispensary software (BestRx / Datascan Winpharm).
Aesthetic: **clinical · trustworthy · fast · data-dense but calm**.

---

## Color palette

| Token | Hex | Use |
|-------|-----|-----|
| Primary | `#1A6DB5` | Actions, active nav, links, focus rings |
| Primary dark | `#145A96` | Hover / pressed primary |
| Primary soft | `#E8F2FB` | Active nav pill, soft highlights |
| Sidebar | `#0B1929` | Persistent left navigation |
| Sidebar hover | `#132337` | Nav item hover |
| Sidebar muted | `#8BA3BC` | Inactive nav labels |
| Workspace BG | `#F0F4F8` | Main content canvas |
| Surface | `#FFFFFF` | Cards, tables, panels |
| Surface soft | `#F7FAFC` | Nested strips, table header |
| Border | `#DDE5EE` | Card / table borders |
| Border strong | `#C5D3E0` | Input borders |
| Text | `#0F1C2E` | Headings, primary copy |
| Text muted | `#5B6B7C` | Subtitles, meta |
| Text faint | `#8A9AAB` | Labels, placeholders |
| Success | `#059669` | In-stock, paid, healthy |
| Success BG | `#ECFDF5` | Success badges / rows |
| Warning | `#D97706` | Expiring soon, caution |
| Warning BG | `#FFFBEB` | Warning badges |
| Danger | `#DC2626` | Low stock, errors, delete |
| Danger BG | `#FEF2F2` | Danger badges / alert rows |
| Info | `#0284C7` | Neutral status / info chips |
| Info BG | `#EFF6FF` | Info chips |

**Status accents only** use green / amber / red. No decorative purple or neon glow.

---

## Typography

Stack: `"DM Sans", "Segoe UI Variable", "Segoe UI", Arial, sans-serif`

| Role | Size | Weight |
|------|------|--------|
| App brand (sidebar) | 15–16px | 800 |
| Page title | 22–24px | 800 |
| Panel title | 14–15px | 700 |
| KPI value | 26–28px | 800 |
| Body / table | 12–13px | 400–600 |
| Meta / captions | 11px | 600 |
| Section eyebrow | 10px | 700 · tracking wide · uppercase |

---

## Spacing & radius

- Workspace padding: **24px**
- Card padding: **16–20px**
- Grid gap: **14–16px**
- Card radius: **12px**
- Control radius: **8px**
- Badge radius: **999px** (pill)
- Sidebar width: **248px**
- Top header height: **56px**

---

## Layout chrome

1. **Left sidebar** — dark navy, brand mark, section groups, icon + label nav, active primary-blue pill, logout at bottom.
2. **Top header** — white bar: menu toggle, pharmacy name, global search, live date/time, user chip.
3. **Content** — soft blue-gray canvas; white elevated panels; desktop-first (≥1024px), usable on tablet.

---

## Components

| Component | Style notes |
|-----------|-------------|
| Primary button | Solid primary blue, white text, 8px radius, 36px min height |
| Success button | Emerald fill (checkout / confirm) |
| Danger button | Red fill (delete / close shift) |
| Secondary button | White + border strong |
| Ghost button | Transparent, primary text |
| Text field | White, 1px border, blue focus ring |
| Search | Same as field; prompt like pharmacy barcode search |
| Card / panel | White, 1px border, soft shadow |
| KPI card | Icon tile + label + large value + hint line |
| Badge | Pill; success/warning/danger/info soft backgrounds |
| Table | Compact rows (~32px), header soft gray, hover `#F7FAFC`, selected `#E8F2FB` |
| Modal | Standard JavaFX dialogs; keep field styling consistent |
| Empty / loading / error | Centered icon + short message inside panel |

---

## Screen priorities (build order)

1. Design tokens + shell (`MainView`)
2. Dashboard
3. Inventory
4. POS Billing
5. Remaining modules (reuse tokens)

---

## Integration notes

- Prefer `PharmacyDesignSystem` factories over one-off inline styles.
- Controllers stay unchanged — views keep the same public wiring (tables, fields, labels).
- Global polish lives in `src/main/resources/css/styles.css`.
