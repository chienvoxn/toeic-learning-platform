import assert from "node:assert/strict";
import { mkdir, writeFile, readFile } from "node:fs/promises";
import { chromium } from "playwright-core";
import { pathToFileURL } from "node:url";
import path from "node:path";

const output = "docs/visual/reports/current";
await mkdir(output, { recursive: true });
const browser = await chromium.launch({
  executablePath:
    process.env.CHROME_PATH ||
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
  headless: true,
});
const errors = [],
  captures = [],
  imageSlots = [];
try {
  const page = await browser.newPage({
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 1,
  });
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(process.env.APP_URL || "http://localhost:8083");
  await page
    .getByRole("button", { name: "Đăng nhập tài khoản", exact: true })
    .waitFor();
  await page.evaluate(() => document.fonts.ready);
  async function capture(name) {
    await page.evaluate(() =>
      Promise.all(
        [...document.images].map((i) =>
          i.complete
            ? Promise.resolve()
            : new Promise((r) => {
                i.onload = r;
                i.onerror = r;
              }),
        ),
      ),
    );
    const images = await page.locator("img").evaluateAll((elements) =>
      elements.map((i) => {
        const r = i.getBoundingClientRect();
        return {
          src: i.getAttribute("src"),
          width: r.width,
          height: r.height,
          loaded: i.complete && i.naturalWidth > 0,
        };
      }),
    );
    assert.ok(
      images.every((i) => i.loaded && i.width > 0 && i.height > 0),
      `${name}: broken image or empty geometry`,
    );
    imageSlots.push({ screen: name, images });
    await page.screenshot({ path: `${output}/${name}.png` });
    captures.push(name);
  }
  const tab = async (name) =>
    page.getByRole("button", { name, exact: true }).click();
  await capture("home");
  await tab("Đăng nhập tài khoản");
  await page.getByText("Chào mừng trở lại", { exact: true }).waitFor();
  await page.setViewportSize({ width: 375, height: 734 });
  await capture("login");
  await tab("Đóng đăng nhập");
  await page.setViewportSize({ width: 390, height: 844 });
  await tab("Học tập");
  await capture("catalog");
  for (const [title, name] of [
    ["TOEIC LISTENING", "catalog-toeic"],
    ["US UK SONGS", "catalog-songs"],
    ["Fairy Tales", "catalog-stories"],
  ]) {
    await page
      .getByText(title, { exact: true })
      .evaluate((e) => e.scrollIntoView({ block: "start" }));
    await capture(name);
  }
  await tab("Lọc bài học");
  await page
    .getByRole("textbox", { name: "Tìm bài học" })
    .fill("no-such-lesson");
  await page.getByText("Không tìm thấy bài học phù hợp.").waitFor();
  await page.getByRole("textbox", { name: "Tìm bài học" }).fill("");
  await tab("Lọc bài học");
  await tab("Từ vựng");
  await capture("vocabulary");
  await page.setViewportSize({ width: 390, height: 1074 });
  await capture("vocabulary-figma-height");
  await page.setViewportSize({ width: 390, height: 844 });
  await page
    .getByRole("button", {
      name: "Xem Từ vựng tiếng Anh giao tiếp",
      exact: true,
    })
    .click();
  await page
    .getByText("Conversational English Vocabulary", { exact: true })
    .waitFor();
  await tab("Quay lại danh sách từ vựng");
  await page
    .getByRole("button", {
      name: "Xem 1000 từ tiếng Anh thông dụng",
      exact: true,
    })
    .click();
  await capture("deck-detail");
  await page.getByRole("button", { name: "Bắt đầu học", exact: true }).click();
  await page.getByText("parents", { exact: true }).waitFor();
  await capture("flashcard-front");
  await page.getByRole("button", { name: /Lật thẻ/ }).click();
  await page.getByText("cha mẹ", { exact: true }).waitFor();
  await capture("flashcard-revealed");
  await tab("Đánh dấu đã thuộc");
  await page.getByText("family", { exact: true }).waitFor();
  await tab("Đóng ôn tập");
  await tab("Cài đặt");
  await capture("settings");
  await page.getByText("Xem lại hướng dẫn học", { exact: true }).click();
  await capture("onboarding-proficiency");
  await page
    .getByText("Tôi có thể nói về nhiều chủ đề", { exact: true })
    .click();
  await capture("onboarding-proficiency-selected");
  await page
    .getByText("Tôi có thể nói sâu vào hầu hết các chủ đề", { exact: true })
    .click();
  await capture("onboarding-proficiency-most-topics");
  await tab("Tiếp tục");
  await capture("onboarding-daily-goal-five");
  await page.getByText("10 phút / ngày", { exact: true }).click();
  await capture("onboarding-daily-goal-ten");
  await page.getByText("20 phút / ngày", { exact: true }).click();
  await capture("onboarding-daily-goal-twenty");
  await page.getByText("15 phút / ngày", { exact: true }).click();
  await capture("onboarding-daily-goal");
  await tab("Tiếp tục");
  await capture("onboarding-benefits");
  await tab("Bắt đầu thôi");
  await tab("Game");
  await capture("games");
  await page
    .getByRole("button", { name: "Chơi ngay →", exact: true })
    .nth(3)
    .click();
  await capture("word-match-ready");
  await page.clock.install();
  await tab("Bắt đầu");
  await capture("word-match-playing");
  await page.getByRole("button", { name: "badminton", exact: true }).click();
  await capture("word-match-selected");
  await page.getByRole("button", { name: "cầu lông", exact: true }).click();
  await capture("word-match-matched");
  await tab("Tạm dừng");
  await page.clock.runFor(3000);
  await page.getByText("Đã tạm dừng", { exact: true }).waitFor();
  await tab("Tiếp tục");
  await page.clock.runFor(35000);
  await capture("word-match-urgent");
  await page.clock.runFor(11000);
  await page.getByText("Hết giờ!", { exact: true }).waitFor();
  await capture("word-match-time-up");
  await tab("Chơi lại");
  await page.getByText("Sẵn sàng nối từ?", { exact: true }).waitFor();
  await capture("word-match-replay");
  await tab("Bắt đầu");
  await tab("Về danh mục game");
  await tab("Hôm nay");
  await page.setViewportSize({ width: 360, height: 800 });
  await capture("home-360");
  await page.setViewportSize({ width: 430, height: 932 });
  await tab("Từ vựng");
  await capture("vocabulary-430");
  assert.deepEqual(errors, []);
  const gallery = await browser.newPage();
  await gallery.goto(
    pathToFileURL(path.resolve("assets/image-gallery.html")).href,
  );
  assert.equal(await gallery.locator("article").count(), 61);
  assert.ok(
    await gallery
      .locator("img")
      .evaluateAll((images) =>
        images.every((i) => i.complete && i.naturalWidth > 0),
      ),
  );
  const downloadEvent = gallery.waitForEvent("download");
  await gallery
    .getByRole("link", { name: "Tải PNG", exact: true })
    .first()
    .click();
  const download = await downloadEvent;
  assert.equal(await download.failure(), null);
  assert.deepEqual(
    await readFile(await download.path()),
    await readFile("assets/images/analytics/let-her-go.png"),
  );
  await gallery.close();
  await writeFile(
    `${output}/verification.json`,
    JSON.stringify(
      {
        viewport: { width: 390, height: 844 },
        captures,
        imageSlots,
        errors,
        checks: [
          "tabs",
          "all visible image files loaded",
          "search empty state",
          "selected deck routing",
          "flashcard reveal and next",
          "onboarding choices and finish",
          "match pair",
          "pause",
          "timeout",
          "replay",
          "360 and 430 widths",
          "61 gallery images and offline PNG download",
        ],
      },
      null,
      2,
    ) + "\n",
  );
  console.log(
    `PASS: ${captures.length} screenshots; navigation, image loading, filters, review, onboarding and game interactions.`,
  );
} finally {
  await browser.close();
}
