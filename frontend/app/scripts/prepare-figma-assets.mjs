import { readFile, writeFile, mkdir, copyFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import { chromium } from "playwright-core";

const root = fileURLToPath(new URL("../", import.meta.url));
const inventory = JSON.parse(
  await readFile(path.join(root, "docs/figma/export-inventory.json"), "utf8"),
);
const assets = [];
const embedded = [
  ["analytics.song", "image0_42_3748", "analytics/let-her-go.png"],
  ["analytics.phrase", "image1_42_3748", "analytics/day-by-day.png"],
  ["profile.avatar", "image0_45_2653", "profile/avatar.png"],
  ...[
    "note",
    "review",
    "progress",
    "trophy",
    "brain",
    "star",
    "link",
    "terms",
    "privacy",
    "mail",
  ].map((name, i) => [
    `profile.${name}`,
    `image${i + 1}_45_2653`,
    `profile/${name}.png`,
  ]),
  ...["home", "catalog", "games", "settings"].map((name, i) => [
    `nav.${name}`,
    `image${i}_45_2871`,
    `navigation/${name}.png`,
  ]),
  ...["rocket", "rain", "bee", "parrot", "meteor", "puzzle"].map((name, i) => [
    `games.${name}`,
    `image${i}_90_2376`,
    `games/${name}.png`,
  ]),
  ...["star", "book", "calendar"].map((name, i) => [
    `onboarding.${name}`,
    `image${i + 2}_42_2887`,
    `onboarding/${name}.png`,
  ]),
];
async function record(id, relativePath, source, details) {
  const bytes = await readFile(path.join(root, relativePath));
  const callsite = id.startsWith("auth.")
    ? "src/features/auth/screens/LoginScreen.js"
    : id.startsWith("profile.")
      ? "src/features/profile/screens/SettingsScreen.js"
      : id.startsWith("nav.")
        ? "src/components/organisms/BottomTabBar.js"
        : id.startsWith("onboarding.")
          ? "src/features/onboarding/screens/OnboardingScreen.js"
          : id.startsWith("analytics.")
            ? "src/features/analytics/screens/HomeScreen.js"
            : id.startsWith("games.")
              ? "src/features/games/screens/GamesScreen.js"
              : id === "vocabulary.parents"
                ? "src/features/vocabulary/screens/FlashcardScreen.js"
                : id.startsWith("vocabulary.cover-")
                  ? "src/features/vocabulary/screens/VocabScreen.js"
                  : "src/features/vocabulary/screens/DeckDetailScreen.js";
  assets.push({
    id,
    relativePath,
    sourceRelativePath: source.sourceRelativePath,
    sourcePath: "docs/figma/exports/" + source.sourceRelativePath,
    callsite,
    downloadUrl: "../" + relativePath,
    status: "LOCAL_VERIFIED_SOURCE",
    sha256: createHash("sha256").update(bytes).digest("hex"),
    ...details,
  });
}
for (const [id, elementId, name] of embedded) {
  const source = inventory.screens.find((s) =>
    s.images.some((i) => i.elementId === elementId),
  );
  const image = source.images.find((i) => i.elementId === elementId);
  const relativePath = "assets/images/" + name;
  await mkdir(path.dirname(path.join(root, relativePath)), { recursive: true });
  await copyFile(
    path.join(root, image.extractedPath),
    path.join(root, relativePath),
  );
  await record(id, relativePath, source, {
    elementId,
    width: image.width,
    height: image.height,
    method: "EMBEDDED_PNG_ORIGINAL_BYTES",
  });
}
// Isolate original graphic layers, not a crop of a rendered screen. Definitions,
// transforms and clipping remain in the derived SVG; render it at 3x for native.
const vector = [
  [
    "onboarding.level-3-default",
    "div#Frame-Screen-Option4-Selected.svg",
    [79, 519.5, 48, 48],
    "onboarding/level-3-default",
  ],
  [
    "onboarding.level-4-selected",
    "div#Frame-Screen-Option4-Selected.svg",
    [79, 611, 48, 48],
    "onboarding/level-4-selected",
  ],
  [
    "onboarding.level-5",
    "div#Frame-Screen-Option5-Selected.svg",
    [79, 490.5, 48, 48],
    "onboarding/level-5",
  ],
  ["auth.mascot", "Frame 3.svg", [165, 155, 115, 135], "auth/login-mascot"],
  [
    "auth.google-logo",
    "Frame 3.svg",
    [131, 478, 23, 23],
    "auth/google-logo",
    'g[clip-path="url(#clip1_45_2452)"]',
  ],
  [
    "onboarding.mascot",
    "div#Frame-Screen-Daily-Goal.svg",
    [184, 100, 92, 102],
    "onboarding/mascot",
  ],
  [
    "vocabulary.parents",
    "Frame 2.svg",
    [76, 199, 74, 74],
    "vocabulary/parents",
  ],
  ...Array.from({ length: 10 }, (_, i) => [
    `vocabulary.group-${i + 1}`,
    "Frame 6.svg",
    [72, 318 + 90 * i, 54, 54],
    `vocabulary/groups/group-${i + 1}`,
  ]),
  [
    "vocabulary.stat-cards",
    "Frame 6.svg",
    [72, 116, 44, 44],
    "vocabulary/stat-cards",
  ],
  [
    "vocabulary.stat-groups",
    "Frame 6.svg",
    [257, 116, 44, 44],
    "vocabulary/stat-groups",
  ],
  ...[
    [79, 355.5],
    [79, 445.5],
    [79, 537],
    [79, 628.5],
  ].map(([x, y], i) => [
    `onboarding.level-${i + 1}`,
    "div#Frame-Screen-Option3-Selected.svg",
    [x, y, 48, 48],
    `onboarding/level-${i + 1}`,
  ]),
  ...[
    [79, 355.5],
    [79, 451.5],
    [76, 549],
    [76, 650],
  ].map(([x, y], i) => [
    `onboarding.goal-${[5, 10, 15, 20][i]}`,
    "div#Frame-Screen-Daily-Goal.svg",
    [x, y, 56, 56],
    `onboarding/goal-${[5, 10, 15, 20][i]}`,
  ]),
  ...[
    [56, 189],
    [241, 189],
    [56, 542.119],
    [241, 542.119],
    [56, 840.244],
    [241, 840.244],
    [56, 1138.37],
    [241, 1138.37],
  ].map(([x, y], i) => [
    `vocabulary.cover-${i + 1}`,
    "Frame 5.svg",
    [x, y, 173, 150],
    `vocabulary/cover-${i + 1}`,
    `g[clip-path="url(#clip${2 + i * 4 - (i > 6 ? 1 : 0)}_45_2871)"]`,
  ]),
];
const browser = await chromium.launch({
  executablePath:
    process.env.CHROME_PATH ||
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
  headless: true,
});
try {
  const page = await browser.newPage({ deviceScaleFactor: 3 });
  for (const [id, file, bounds, name, selector] of vector) {
    const source = inventory.screens.find((s) => s.sourceRelativePath === file);
    await page.setContent(
      await readFile(path.join(root, source.referencePath), "utf8"),
    );
    const svg = await page.evaluate(
      ({ bounds: [x, y, w, h], selector }) => {
        const original = document.querySelector("svg");
        if (selector) {
          const graphic = original.querySelector(selector)?.cloneNode(true);
          if (!graphic) throw new Error("Graphic group not found: " + selector);
          const defs = original.querySelector("defs").cloneNode(true);
          original.replaceChildren(graphic, defs);
          original.setAttribute("viewBox", `${x} ${y} ${w} ${h}`);
          original.setAttribute("width", w);
          original.setAttribute("height", h);
          return new XMLSerializer().serializeToString(original);
        }
        const keep = [];
        const elements = [
          ...original.querySelectorAll(
            "path,rect,use,circle,ellipse,polygon,line,polyline,image,foreignObject",
          ),
        ].filter((e) => !e.closest("defs"));
        for (const e of elements) {
          const b = e.getBBox(),
            m = e.getCTM();
          const corners = [
            [b.x, b.y],
            [b.x + b.width, b.y + b.height],
          ].map(([cx, cy]) => new DOMPoint(cx, cy).matrixTransform(m));
          const inside = corners.every(
            (p) =>
              p.x >= x - 0.1 &&
              p.x <= x + w + 0.1 &&
              p.y >= y - 0.1 &&
              p.y <= y + h + 0.1,
          );
          if (inside) keep.push(e);
          else e.remove();
        }
        if (!keep.length) throw new Error("Empty vector selection");
        // The outer device shadow belongs to the reference, not to this graphic.
        original.querySelector(":scope > g")?.removeAttribute("filter");
        original.setAttribute("viewBox", `${x} ${y} ${w} ${h}`);
        original.setAttribute("width", w);
        original.setAttribute("height", h);
        return new XMLSerializer().serializeToString(original);
      },
      { bounds, selector },
    );
    const relativePath = "assets/images/" + name + ".png";
    await mkdir(path.dirname(path.join(root, relativePath)), {
      recursive: true,
    });
    await writeFile(path.join(root, "assets/images/" + name + ".svg"), svg);
    await page.setViewportSize({ width: bounds[2], height: bounds[3] });
    await page.setContent(
      "<style>html,body{margin:0;background:transparent}svg{display:block}</style>" +
        svg,
    );
    await page.screenshot({
      path: path.join(root, relativePath),
      omitBackground: true,
    });
    await record(id, relativePath, source, {
      sourceBounds: bounds,
      width: bounds[2] * 3,
      height: bounds[3] * 3,
      method: "ORIGINAL_VECTOR_LAYERS_RENDERED_3X",
    });
  }
} finally {
  await browser.close();
}
await writeFile(
  path.join(root, "assets/asset-manifest.json"),
  JSON.stringify(
    {
      schemaVersion: 2,
      fileKey: "BCKDxbTbgk68d89p3wxcec",
      figmaAccess: "LOCAL_USER_SVG_EXPORT",
      sourceDirectory: inventory.sourceDirectory,
      notice:
        "All runtime paths relative to frontend/app. downloadUrl relative to assets/image-gallery.html. No external image host required. Original exports are references only.",
      assets,
    },
    null,
    2,
  ) + "\n",
);
console.log(
  `Prepared ${assets.length} local assets from original PNG bytes and isolated vector graphics.`,
);
