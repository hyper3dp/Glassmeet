import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'
import { fileURLToPath } from 'node:url'
import { copyFile, readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'

// GitHub Pages only serves files that exist, so client-side routes like /privacy
// answer with a 404 status (404.html then redirects into the app). Crawlers such as
// Google's OAuth verification see that 404. Copying index.html to <route>.html lets
// Pages serve these routes directly with a 200; the app's router renders the page.
// served as real files, so links to them (including auth links carrying #tokens) skip 404.html
const STATIC_ROUTES = ['privacy', 'terms', 'login', 'register', 'forgot-password', 'reset-password', 'verify', 'dashboard'];

function staticRoutes() {
  let outDir;
  return {
    name: 'glassmeet-static-routes',
    apply: 'build',
    configResolved(config) {
      outDir = path.resolve(config.root, config.build.outDir);
    },
    async closeBundle() {
      await Promise.all(STATIC_ROUTES.map((route) =>
        copyFile(path.join(outDir, 'index.html'), path.join(outDir, `${route}.html`))));
    },
  };
}

// GitHub Pages can't send response headers, so the policies a page can set for itself go in
// <meta> tags: a Content-Security-Policy (each inline script allowed by its hash, not by
// 'unsafe-inline') and a Referrer-Policy. Build only: the dev server injects its own inline
// scripts. frame-ancestors, X-Frame-Options, X-Content-Type-Options and Permissions-Policy only
// work as real headers; public/_headers sets those on a host that supports it, and index.html
// guards against framing in the meantime.
const SUPABASE_ORIGIN = 'https://sjxraemrdpzleipfmwun.supabase.co'; // as in src/lib/supabaseClient.js

const inlineScriptHashes = (html) =>
  [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)]
    .filter((m) => m[1].trim())
    // browsers hash the script as parsed, where CRLF line endings have become LF
    .map((m) => `'sha256-${createHash('sha256').update(m[1].replace(/\r\n?/g, '\n')).digest('base64')}'`);

const securityMeta = (html) => {
  const csp = [
    "default-src 'self'",
    `script-src 'self' ${inlineScriptHashes(html).join(' ')}`.trim(),
    // React style={...} attributes and the inline background <style> need inline styles
    "style-src 'self' 'unsafe-inline'",
    // avatars (Supabase storage, Google photos) and QR codes come from other https hosts
    "img-src 'self' data: blob: https:",
    "font-src 'self' data:",
    `connect-src 'self' ${SUPABASE_ORIGIN} ${SUPABASE_ORIGIN.replace('https://', 'wss://')}`,
    "frame-src 'self'",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    'upgrade-insecure-requests',
  ].join('; ');
  const tags = `<meta http-equiv="Content-Security-Policy" content="${csp}" />\n    <meta name="referrer" content="strict-origin-when-cross-origin" />\n    `;
  return html.replace(/<meta charset="utf-8" \/>\s*/i, (m) => `${m}${tags}`);
};

function securityHeaders() {
  let outDir;
  return {
    name: 'glassmeet-security-meta',
    apply: 'build',
    configResolved(config) {
      outDir = path.resolve(config.root, config.build.outDir);
    },
    transformIndexHtml: { order: 'post', handler: securityMeta },
    // public/404.html is copied as it is; give it the same tags
    async writeBundle() {
      const file = path.join(outDir, '404.html');
      await writeFile(file, securityMeta(await readFile(file, 'utf8')));
    },
  };
}

// Without help, a page's own files are only found once index.js has downloaded and run, and the
// font once the CSS has been read: two round trips before anything is drawn. A small inline
// script in <head> starts them alongside index.js instead: the files of the page being opened
// (from the same routes as src/lib/pages.js) and Supabase when the visit needs it (the same rule
// as maySignIn() in src/lib/supabaseClient.js and STATIC_PAGES in pages.js). Build only.
const PAGE_FILES = {
  '/': ['src/pages/Landing.jsx'],
  '/login': ['src/pages/login.jsx'],
  '/register': ['src/pages/register.jsx'],
  '/forgot-password': ['src/pages/forgotpassword.jsx'],
  '/reset-password': ['src/pages/resetpassword.jsx'],
  '/verify': ['src/pages/verify.jsx'],
  '/terms': ['src/pages/terms.jsx'],
  '/privacy': ['src/pages/privacy.jsx'],
  '/dashboard': ['src/Components/AppShell.jsx', 'src/pages/dashboard.jsx'],
  '/calendar': ['src/Components/AppShell.jsx', 'src/pages/calenderpage.jsx'],
  '/event-types': ['src/Components/AppShell.jsx', 'src/pages/Event types.jsx'],
  '/bookings': ['src/Components/AppShell.jsx', 'src/pages/bookings.jsx'],
  '/availability': ['src/Components/AppShell.jsx', 'src/pages/Availability.jsx'],
  '/whatsapp': ['src/Components/AppShell.jsx', 'src/pages/whatsappsettings.jsx'],
  '/settings': ['src/Components/AppShell.jsx', 'src/pages/settings.jsx'],
  '/calendar-connections': ['src/Components/AppShell.jsx', 'src/pages/calenderconnections.jsx'],
  '/analytics': ['src/Components/AppShell.jsx', 'src/pages/Analytics.jsx'],
  book: ['src/pages/bookingpage.jsx'],
  confirm: ['src/pages/bookingconfirmation.jsx'],
};
const SESSION_KEY = `sb-${new URL(SUPABASE_ORIGIN).hostname.split('.')[0]}-auth-token`;

function routePreloads() {
  let root;
  let base;
  return {
    name: 'glassmeet-route-preloads',
    apply: 'build',
    configResolved(config) {
      root = config.root;
      base = config.base;
    },
    transformIndexHtml: {
      order: 'post',
      handler(html, ctx) {
        const chunks = Object.values(ctx.bundle || {}).filter((c) => c.type === 'chunk');
        const byFile = new Map(chunks.map((c) => [c.fileName, c]));
        const deps = (file, seen = new Set()) => {
          if (seen.has(file)) return seen;
          seen.add(file);
          for (const dep of byFile.get(file)?.imports || []) deps(dep, seen);
          return seen;
        };
        // index.html preloads these already
        const entry = chunks.find((c) => c.isEntry);
        const loaded = entry ? deps(entry.fileName) : new Set();
        const rel = (id) => (id ? path.relative(root, id).split(path.sep).join('/') : '');
        const files = (sources) => {
          const out = new Set();
          for (const src of sources) {
            const chunk = chunks.find((c) => rel(c.facadeModuleId) === src);
            if (!chunk) throw new Error(`glassmeet-route-preloads: no chunk for ${src}`);
            for (const f of deps(chunk.fileName)) if (!loaded.has(f)) out.add(f);
          }
          return [...out];
        };
        // each file named once (F); routes list positions in it, which keeps the HTML small
        const table = [];
        const at = (f) => { let i = table.indexOf(f); if (i < 0) i = table.push(f) - 1; return i; };
        const routes = Object.fromEntries(Object.entries(PAGE_FILES).map(([route, sources]) => [route, files(sources).map(at)]));
        const supabase = chunks.filter((c) => c.name === 'supabase').map((c) => at(c.fileName));
        const names = table.map((f) => f.replace(/^assets\//, '').replace(/\.js$/, ''));
        const font = Object.keys(ctx.bundle).find((f) => /figtree-latin-wght-normal-[^/]*\.woff2$/.test(f));
        const script = `(function () {
        var F = ${JSON.stringify(names)}, R = ${JSON.stringify(routes)}, S = ${JSON.stringify(supabase)}, base = ${JSON.stringify(base)};
        var p = location.pathname.slice(base.length - 1).replace(/\\/+$/, '') || '/';
        var key = R[p] ? p : /^\\/book\\/./.test(p) ? 'book' : /^\\/confirm\\/./.test(p) ? 'confirm' : null;
        var list = key ? R[key].slice() : [];
        var signedIn = /[#&](access_token|refresh_token|error_description)=/.test(location.hash) || /[?&](code|token_hash|error_description)=/.test(location.search);
        try { signedIn = signedIn || localStorage.getItem(${JSON.stringify(SESSION_KEY)}) !== null; } catch (e) { signedIn = true; }
        if (signedIn || (key && !/^\\/(terms|privacy)?$/.test(p))) list = list.concat(S);
        list.forEach(function (i) {
          var l = document.createElement('link');
          l.rel = 'modulepreload';
          l.crossOrigin = '';
          l.href = base + 'assets/' + F[i] + '.js';
          document.head.appendChild(l);
        });
      })();`;
        const fontTag = font ? `<link rel="preload" href="${base}${font}" as="font" type="font/woff2" crossorigin />\n    ` : '';
        // before the stylesheet: an inline script after one waits for it to download first
        const tags = `${fontTag}<script>\n      ${script}\n    </script>\n    `;
        const where = html.search(/<script type="module"/);
        if (where < 0) throw new Error('glassmeet-route-preloads: no module script in index.html');
        return html.slice(0, where) + tags + html.slice(where);
      },
    },
  };
}

export default defineConfig({
  base: '/',
  // routePreloads before securityHeaders, whose CSP hashes every inline script
  plugins: [react(), tailwindcss(), routePreloads(), securityHeaders(), staticRoutes()],
  build: {
    rolldownOptions: {
      output: {
        // Libraries every page loads get chunks of their own: they change far less often than the
        // app's code, so browsers keep them cached across deploys. Everything else splits by page.
        codeSplitting: {
          groups: [
            { name: 'react', test: /[\\/]node_modules[\\/](react|react-dom|scheduler|react-router|react-router-dom|@remix-run[\\/]router)[\\/]/, priority: 2 },
            // tslib gets its own: Radix's scroll lock uses it too, and inside the Supabase chunk it
            // would drag all of Supabase into every page that opens a dialog
            { name: 'tslib', test: /[\\/]node_modules[\\/]tslib[\\/]/, priority: 3 },
            { name: 'supabase', test: /[\\/]node_modules[\\/](@supabase[\\/]|iceberg-js[\\/])/, priority: 1 },
          ],
        },
      },
    },
  },
  resolve: {
    alias: [
      { find: '@/components', replacement: fileURLToPath(new URL('./src/Components', import.meta.url)) },
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
    ],
  },
});
