# Catalogue icons

Curated Phosphor Icons duotone assets from `@phosphor-icons/core` 2.1.1. Source: https://github.com/phosphor-icons/core. Distributed under the included MIT licence.

`catalog.json` is the source of truth for supported keys, labels, search tags and colours. The SVGs are upstream source assets. Run `npm run icons:generate` after editing this catalogue. `npm run icons:check` verifies Studio assets, Android vector resources, the API allowlist and the SQL allowlist. Icons load locally; no third-party CDN or paid icon account is required. To add an icon, copy its duotone SVG from the same upstream release into `svg/`, add its catalogue entry, and create a new SQL migration extending the key constraint for deployed databases.
