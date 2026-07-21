# Deploying the site to `manticview.anticarbon.me`

The site is a plain static site (HTML + `assets/`) served from **GitHub Pages**
at the custom subdomain **`manticview.anticarbon.me`**. Publishing is automated
by [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml), which runs on
every push to `main` and deploys everything except the source-only folders
(`android/`, `apps-script/`).

This deployment is owned by the fork **`nova-si14/manticView`** — that fork is
where GitHub Pages is enabled and where the subdomain's DNS points. The upstream
`JesusDeSivar/manticView` just carries this config; it does **not** enable Pages
(see [Why the upstream leaves Pages off](#why-the-upstream-leaves-pages-off)).

Three one-time steps are required outside the repo. They can't be done in code.

## 1. Get this config onto your fork's `main`

- **Fresh fork:** fork `JesusDeSivar/manticView` after this config is on its
  `main` and your fork's `main` already has it — nothing to do.
- **Existing fork:** open your fork on GitHub and click **Sync fork → Update
  branch** so its `main` picks up this config.

## 2. Enable GitHub Pages on your fork (repo setting)

In **`nova-si14/manticView`**: **Settings → Pages → Build and deployment →
Source** → select **GitHub Actions**.

The workflow does the rest. After it runs, the same Pages settings page shows the
custom domain (read from the [`CNAME`](CNAME) file) and, once DNS resolves, an
**Enforce HTTPS** checkbox — tick it.

## 3. Add the DNS record (at your domain)

At whoever manages DNS for `anticarbon.me`, add:

| Type  | Name / Host  | Value / Target        |
| ----- | ------------ | --------------------- |
| CNAME | `manticview` | `nova-si14.github.io` |

Notes:

- The target is **your fork owner's** Pages host (`nova-si14.github.io`), not the
  repo path. GitHub matches the request to your fork via the `CNAME` file.
- This is a brand-new subdomain, so it does **not** affect the apex
  `anticarbon.me` / `www.anticarbon.me` site.
- **If DNS is on Cloudflare:** set the record to **DNS only** (grey cloud) so
  GitHub can issue the TLS certificate. You can switch it back to proxied later.
- **Recommended:** verify `anticarbon.me` under your GitHub account
  (**Settings → Pages → "Verify a domain"** at the account level) so no other
  account can attach a subdomain of it.

DNS + certificate provisioning usually completes within minutes, occasionally up
to a few hours.

## Why the upstream leaves Pages off

A custom domain can only be *actively served* by one GitHub Pages site at a time,
decided by where the DNS points. Since `manticview.anticarbon.me` resolves to
`nova-si14.github.io`, only your fork serves it. If the upstream
`JesusDeSivar/manticView` also enabled Pages with this same `CNAME`, its Pages
settings would just show the domain as misconfigured — harmless, but avoid it by
leaving the upstream's Pages source off.

## Changing the domain

Edit [`CNAME`](CNAME) (one line, the bare hostname — no `https://`), update the
matching DNS record, and push.
