import assert from "node:assert/strict";
import { test } from "node:test";
import {
  contentRepository,
  filterContents,
} from "../src/features/content/repositories/content.mock.js";

test("category and query filter together, including empty results", () => {
  const items = contentRepository.getContents();
  assert.deepEqual(
    filterContents(items, "TOEIC", "TEST 1").map((item) => item.title),
    ["Test 1 Part 1"],
  );
  assert.equal(filterContents(items, "Phim", "TOEIC").length, 0);
  assert.equal(filterContents(items, "Tất cả", "").length, items.length);
});

test("consumer changes do not mutate the repository data", () => {
  const items = contentRepository.getContents();
  items[0].title = "changed";
  assert.notEqual(contentRepository.getContents()[0].title, "changed");
});
