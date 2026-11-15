import { expect, test, type Browser, type Page } from '@playwright/test';
import { solidPng } from './png';

const PASSWORD = 'E2e-passw0rd';
const run = Date.now().toString(36);

async function signUp(browser: Browser, name: string, type: 'I have an audience' | 'I want to advertise') {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/signup');
  await page.getByRole('radio', { name: new RegExp(type) }).click();
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Email').fill(`${name.toLowerCase().replace(/\W+/g, '.')}.${run}@e2e.test`);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Browse' })).toBeVisible();
  return page;
}

async function openThread(page: Page, otherName: string) {
  await page.getByRole('link', { name: /^Messages/ }).click();
  await page
    .getByRole('list', { name: 'Conversations' })
    .getByRole('link', { name: new RegExp(otherName) })
    .click();
  return page.locator('section');
}

test('advertiser publishes, marketer offers and they negotiate in real time', async ({ browser }) => {
  const title = `Launch streamers ${run}`;
  const advertiser = await signUp(browser, `Northwind ${run}`, 'I want to advertise');

  await advertiser.getByRole('link', { name: 'New posting' }).click();
  await expect(advertiser.getByRole('heading', { name: 'New campaign' })).toBeVisible();
  await advertiser.getByLabel('Title').fill(title);
  await advertiser.getByLabel('Description').fill('Two sessions during launch week.');
  await advertiser.locator('input[type=file]').setInputFiles({
    name: 'key-art.png',
    mimeType: 'image/png',
    buffer: solidPng(64, 32, [40, 90, 80]),
  });
  await expect(advertiser.getByLabel('Description')).toHaveValue(/!\[\]\(image:[0-9a-f-]{36}\)/);
  await advertiser.getByLabel('From', { exact: true }).fill('1500');
  await advertiser.getByLabel('To', { exact: true }).fill('4000');
  await advertiser.getByRole('button', { name: 'Publish' }).click();

  await expect(advertiser.getByRole('heading', { name: title })).toBeVisible();
  await expect(advertiser.getByText('€1,500 – €4,000')).toBeVisible();
  const image = advertiser.locator('article img');
  await expect(image).toBeVisible();
  expect(await image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(64);

  const marketer = await signUp(browser, `Maya ${run}`, 'I have an audience');
  await marketer.getByRole('link', { name: title }).click();
  await marketer.getByLabel('Your proposal').fill('Two 2-hour sessions with a sponsor segment.');
  await marketer.getByLabel('Price from').fill('2200');
  await marketer.getByLabel('Price to').fill('2800');
  await marketer.getByRole('button', { name: 'Send offer' }).click();
  await expect(marketer.getByText('Your offer')).toBeVisible();
  await expect(marketer.getByText('€2,200 – €2,800')).toBeVisible();

  const advertiserThread = await openThread(advertiser, `Maya ${run}`);
  await expect(advertiserThread.getByText(`Maya ${run} made an offer: 2,200 – 2,800 EUR`)).toBeVisible();

  const marketerThread = await openThread(marketer, `Northwind ${run}`);
  await marketerThread.getByLabel('Message').pressSequentially('Happy to start on launch day', { delay: 20 });
  await expect(advertiserThread.getByText(`Maya is typing…`)).toBeVisible();
  await marketerThread.getByLabel('Message').press('Enter');

  await expect(advertiserThread.getByText('Happy to start on launch day')).toBeVisible();
  await advertiserThread.getByLabel('Message').fill('Great, see you then!');
  await advertiserThread.getByRole('button', { name: 'Send' }).click();
  await expect(marketerThread.getByText('Great, see you then!')).toBeVisible();
  await expect(marketerThread.getByText('Seen')).toBeVisible();

  await advertiser.getByRole('link', { name: title, exact: true }).click();
  await advertiser.getByRole('button', { name: 'Accept' }).click();
  const acceptDialog = advertiser.getByRole('dialog');
  await expect(acceptDialog.getByLabel('Agreed amount (EUR)')).toHaveValue('2200');
  await acceptDialog.getByLabel('Agreed amount (EUR)').fill('3000');
  await expect(acceptDialog.getByRole('button', { name: 'Accept and start deal' })).toBeDisabled();
  await acceptDialog.getByLabel('Agreed amount (EUR)').fill('2500');
  await acceptDialog.getByRole('button', { name: 'Accept and start deal' }).click();
  await expect(advertiser.locator('li', { hasText: `Maya ${run}` }).getByText('Accepted')).toBeVisible();

  await marketer.getByRole('link', { name: 'My offers' }).click();
  await marketer.getByRole('tab', { name: 'Accepted' }).click();
  await expect(marketer.getByRole('link', { name: title })).toBeVisible();

  const marketerThreadAgain = await openThread(marketer, `Northwind ${run}`);
  await expect(marketerThreadAgain.getByText(new RegExp(`Northwind ${run} accepted the offer`))).toBeVisible();

  await marketer.getByRole('link', { name: 'Deals' }).click();
  await expect(marketer.getByRole('heading', { name: 'Deals' })).toBeVisible();
  await marketer.getByRole('link', { name: title, exact: true }).click();
  await expect(marketer.getByText('€2,500')).toBeVisible();
  await marketer.getByLabel('Links').fill('https://www.twitch.tv/videos/123456');
  await marketer.getByRole('button', { name: 'Submit for approval' }).click();
  await expect(marketer.getByText('Awaiting approval').first()).toBeVisible();

  await advertiser.getByRole('link', { name: 'Deals' }).click();
  await expect(advertiser.getByRole('heading', { name: 'Deals' })).toBeVisible();
  await advertiser.getByRole('link', { name: title, exact: true }).click();
  await expect(advertiser.getByRole('link', { name: 'https://www.twitch.tv/videos/123456' }).first()).toBeVisible();
  await advertiser.getByRole('button', { name: 'Approve delivery' }).click();
  await expect(advertiser.getByText('Completed').first()).toBeVisible();

  await advertiser.getByRole('radio', { name: '5 stars' }).click();
  await advertiser.getByLabel('Review').fill('Great streams, on time.');
  await advertiser.getByRole('button', { name: 'Submit review' }).click();
  await expect(advertiser.getByText(`Maya ${run} hasn’t left a review yet.`)).toBeVisible();

  await marketer.reload();
  await expect(
    marketer.getByText(`Northwind ${run} has reviewed you. You’ll see it once you leave yours.`),
  ).toBeVisible();
  await marketer.getByRole('radio', { name: '4 stars' }).click();
  await marketer.getByRole('button', { name: 'Submit review' }).click();
  await expect(marketer.getByText('Great streams, on time.')).toBeVisible();

  await marketer
    .getByRole('link', { name: `Northwind ${run}` })
    .first()
    .click();
  await expect(marketer.getByRole('heading', { name: `Northwind ${run}` })).toBeVisible();
  await expect(marketer.getByText('1 review')).toBeVisible();
});

test('session survives a reload through the refresh cookie', async ({ browser }) => {
  const page = await signUp(browser, `Reload ${run}`, 'I want to advertise');
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('refresh');
  const cookies = await page.context().cookies();
  const refresh = cookies.find((cookie) => cookie.name === 'am_refresh');
  expect(refresh?.httpOnly).toBe(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Browse' })).toBeVisible();
});

test('unauthenticated visitors are sent to log in', async ({ page }) => {
  await page.goto('/my/offers');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();
});
