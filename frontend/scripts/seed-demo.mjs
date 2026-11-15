import { deflateSync } from 'node:zlib';

const API = process.env.API_URL ?? 'http://localhost:8080/api/v1';
const PASSWORD = 'Demo-passw0rd';

function crc32(buffer) {
  let crc = ~0;
  for (const byte of buffer) {
    crc ^= byte;
    for (let k = 0; k < 8; k++) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return ~crc >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function bannerPng(width, height, [r1, g1, b1], [r2, g2, b2]) {
  const raw = Buffer.alloc((width * 3 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 3 + 1)] = 0;
    for (let x = 0; x < width; x++) {
      const t = (x / width) * 0.7 + (y / height) * 0.3;
      const stripe = Math.floor((x + y) / 28) % 2 === 0 ? 0 : 6;
      const offset = y * (width * 3 + 1) + 1 + x * 3;
      raw[offset] = Math.round(r1 + (r2 - r1) * t) - stripe;
      raw[offset + 1] = Math.round(g1 + (g2 - g1) * t) - stripe;
      raw[offset + 2] = Math.round(b1 + (b2 - b1) * t) - stripe;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

async function call(path, { token, method = 'GET', body, form } = {}) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: form ?? (body ? JSON.stringify(body) : undefined),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`${method} ${path} → ${response.status}: ${text}`);
  }
  return text ? JSON.parse(text) : undefined;
}

async function account(fullName, email, userType) {
  try {
    return await call('/auth/signup', { method: 'POST', body: { fullName, email, userType, password: PASSWORD } });
  } catch {
    return call('/auth/login', { method: 'POST', body: { email, password: PASSWORD } });
  }
}

async function upload(token, png, name) {
  const form = new FormData();
  form.append('file', new Blob([png], { type: 'image/png' }), name);
  return call('/images', { token, method: 'POST', form });
}

const posting = (token, body) => call('/postings', { token, method: 'POST', body });

const northwind = await account('Northwind Games', 'marketing@northwind.test', 'CUSTOMER');
const lumen = await account('Lumen Coffee Roasters', 'hello@lumencoffee.test', 'CUSTOMER');
const maya = await account('Maya Chen', 'maya@creators.test', 'MARKETER');
const dario = await account('Dario Ferri', 'dario@creators.test', 'MARKETER');
const ines = await account('Ines Popescu', 'ines@creators.test', 'MARKETER');

const banner = await upload(northwind.accessToken, bannerPng(960, 400, [32, 62, 74], [88, 138, 120]), 'skyfall-banner.png');
const launch = await posting(northwind.accessToken, {
  category: 'CUSTOMER_POSTING',
  status: 'OPEN',
  title: 'Gaming streamers for the Skyfall Tactics launch',
  text: `We're launching **Skyfall Tactics**, a turn-based strategy game, on March 14 and want streamers to play it live during launch week.

![Skyfall Tactics key art](image:${banner.id})

## What we're looking for
- Strategy or tactics content, 2,000+ average live viewers
- At least two sessions of 90 minutes during launch week
- A pinned link and a short sponsor segment at the start of each stream

## What we provide
- Early access keys two weeks before launch
- A talking-points brief and assets for overlays

Tell us about your audience and which dates work for you.`,
  payFrom: 1500,
  payTo: 4000,
  payCurrency: 'EUR',
});
await posting(lumen.accessToken, {
  category: 'CUSTOMER_POSTING',
  status: 'OPEN',
  title: 'Morning-routine creators for a subscription coffee box',
  text: 'Looking for lifestyle creators on Instagram or TikTok to feature our monthly coffee box in a morning-routine video. One video plus three stories, with a discount code for your audience.',
  payFrom: 400,
  payTo: 900,
  payCurrency: 'GBP',
});
await posting(northwind.accessToken, {
  category: 'CUSTOMER_POSTING',
  status: 'OPEN',
  title: 'YouTube review of Skyfall Tactics (long-form)',
  text: 'A 10–15 minute honest review, published within two weeks of launch. We won’t review the script, only check the sponsor disclosure.',
  payFrom: 2000,
  payTo: null,
  payCurrency: 'USD',
});
await posting(lumen.accessToken, {
  category: 'CUSTOMER_POSTING',
  status: 'OPEN',
  title: 'Romanian food bloggers for a café opening in Cluj',
  text: 'We’re opening our first café in Cluj-Napoca. Looking for local food bloggers to cover the opening weekend.',
  payFrom: 1500,
  payTo: 3000,
  payCurrency: 'RON',
});
await posting(lumen.accessToken, {
  category: 'CUSTOMER_POSTING',
  status: 'DRAFT',
  title: 'Holiday gift guide placements',
  text: 'Draft: list of gift-guide creators for December.',
  payFrom: null,
  payTo: null,
  payCurrency: null,
});
await posting(maya.accessToken, {
  category: 'MARKETER_POSTING',
  status: 'OPEN',
  title: 'Sponsored segment on my weekly strategy stream',
  text: 'I stream strategy games every Tuesday and Thursday to an average of 3,400 live viewers (Twitch, EU evening slot). I offer a 60-second sponsor read, a pinned chat link and an overlay for the whole session.',
  payFrom: 800,
  payTo: 1200,
  payCurrency: 'EUR',
});
await posting(dario.accessToken, {
  category: 'MARKETER_POSTING',
  status: 'OPEN',
  title: 'Dedicated TikTok video for food and drink brands',
  text: 'Food and coffee content for 180k followers, mostly 20–34 in Italy and the UK. One dedicated video, usage rights for 30 days.',
  payFrom: 600,
  payTo: 600,
  payCurrency: 'EUR',
});

const mayaOffer = await call(`/postings/${launch.id}/offers`, {
  token: maya.accessToken,
  method: 'POST',
  body: { text: 'Two 2-hour sessions on launch day and the Saturday after, with a sponsor segment and pinned link. My audience is mostly strategy players aged 18–34 in the EU.', priceFrom: 2200, priceTo: 2800 },
});
await call(`/postings/${launch.id}/offers`, {
  token: dario.accessToken,
  method: 'POST',
  body: { text: 'One 90-minute session plus a highlights clip on TikTok.', priceFrom: 1500, priceTo: 1500 },
});
const inesOffer = await call(`/postings/${launch.id}/offers`, {
  token: ines.accessToken,
  method: 'POST',
  body: { text: 'Three shorter sessions across launch week, Romanian-speaking audience of ~2,100.', priceFrom: 1800, priceTo: 2400 },
});
await call(`/offers/${inesOffer.id}/status`, { token: northwind.accessToken, method: 'PATCH', body: { status: 'REJECTED' } });

const conversations = await call(`/conversations?postingId=${launch.id}`, { token: northwind.accessToken });
const withMaya = conversations.content.find((c) => c.counterpart.fullName === 'Maya Chen');
const say = (token, text) => call(`/conversations/${withMaya.id}/messages`, { token, method: 'POST', body: { text } });
await say(northwind.accessToken, 'Hi Maya, thanks for the offer! Could you share your average viewers over the last 30 days?');
await say(maya.accessToken, 'Sure, 3,400 average and 5,100 peak. I can send the Twitch analytics screenshot.');
await say(maya.accessToken, 'Also happy to add a giveaway of 20 keys if that helps.');
await say(northwind.accessToken, 'That would be great. Launch day works for us; can you do 7pm CET?');

console.log(`Seeded demo data. Log in as any of these with password ${PASSWORD}:`);
for (const auth of [northwind, lumen, maya, dario, ines]) {
  console.log(`  ${auth.user.email.padEnd(28)} ${auth.user.userType}`);
}
console.log(`Offer from Maya: ${mayaOffer.id}`);
