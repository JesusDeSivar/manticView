# Deploying the site to `manticview.anticarbon.me`

The site is a plain static site (HTML + `assets/`) served from **GitHub Pages**
at the custom subdomain **`manticview.anticarbon.me`**. Publishing is automated
by [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml), which runs on
every push to `main` and deploys everything except the source-only folders
(`android/`, `apps-script/`).

Two one-time steps are required outside the repo. They can't be done in code —
one is a repo setting, one is a DNS record.

## 1. Enable GitHub Pages (repo setting)

In the repository: **Settings → Pages → Build and deployment → Source** →
select **GitHub Actions**.

That's it — the workflow in this repo does the rest. After it runs, the same
Pages settings page will show the custom domain (read from the [`CNAME`](CNAME)
file) and, once DNS resolves, an **Enforce HTTPS** checkbox — tick it.

## 2. Add the DNS record (at your domain)

At whoever manages DNS for `anticarbon.me`, add:

| Type  | Name / Host  | Value / Target          |
| ----- | ------------ | ----------------------- |
| CNAME | `manticview` | `jesusdesivar.github.io` |

Notes:

- The target is the **Pages host of the repo's owner** (`<owner>.github.io`),
  not the repo path. GitHub matches the request to this repo via the `CNAME`
  file. If you fork this repo to another account, change the target to
  `<that-account>.github.io` and update `CNAME` to keep the same subdomain.
- This is a brand-new subdomain, so it does **not** affect the apex
  `anticarbon.me` / `www.anticarbon.me` site.
- **If DNS is on Cloudflare:** set the record to **DNS only** (grey cloud) so
  GitHub can issue the TLS certificate. You can switch it back to proxied later
  if you want.

DNS + certificate provisioning usually completes within minutes, occasionally
up to a few hours.

## Changing the domain

Edit [`CNAME`](CNAME) (one line, the bare hostname — no `https://`), update the
matching DNS record, and push. A custom domain can only be attached to one
GitHub Pages site at a time.
