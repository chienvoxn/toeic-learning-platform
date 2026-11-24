import assert from "node:assert/strict";
import { test } from "node:test";
import { navigationReducer, SCREEN_IDS } from "../src/navigation/routes.js";
import { authStorage } from "../src/services/apiClient.js";
import { authApi } from "../src/services/auth.api.js";
import { aiApi } from "../src/services/ai.api.js";

test("SCREEN_IDS includes newly integrated mobile screens", () => {
  const requiredScreens = ["contentDetail", "aiChat"];
  for (const screen of requiredScreens) {
    assert.ok(SCREEN_IDS.includes(screen), `SCREEN_IDS should contain ${screen}`);
  }
  assert.ok(!SCREEN_IDS.includes("practice"), "SCREEN_IDS must NOT contain practice");
  assert.ok(!SCREEN_IDS.includes("practiceResult"), "SCREEN_IDS must NOT contain practiceResult");
});

test("navigation stack handles contentDetail and aiChat properly", () => {
  let state = ["home"];
  state = navigationReducer(state, { type: "navigate", screen: "aiChat" });
  assert.deepEqual(state, ["home", "aiChat"]);

  state = navigationReducer(state, { type: "back" });
  assert.deepEqual(state, ["home"]);

  state = navigationReducer(state, { type: "tab", screen: "catalog" });
  assert.deepEqual(state, ["catalog"]);

  state = navigationReducer(state, { type: "navigate", screen: "contentDetail" });
  assert.deepEqual(state, ["catalog", "contentDetail"]);

  state = navigationReducer(state, { type: "back" });
  assert.deepEqual(state, ["catalog"]);
});

test("authStorage can set, get and clear token safely", () => {
  authStorage.setToken("test-jwt-bearer-token");
  assert.equal(authStorage.getToken(), "test-jwt-bearer-token");
  authStorage.clearToken();
  assert.equal(authStorage.getToken(), null);
});

test("authApi login returns valid user and token", async () => {
  const result = await authApi.login("student@toeic.edu.vn", "secret123");
  assert.ok(result.success);
  assert.ok(result.user);
  assert.equal(result.user.email, "student@toeic.edu.vn");
});

test("aiApi creates conversation and provides intelligent guidance", async () => {
  const conv = await aiApi.createConversation();
  assert.ok(conv);
  assert.ok(conv.id);

  const answer = await aiApi.askMessage(conv.id, "Mẹo học từ vựng nhớ lâu là gì?");
  assert.ok(answer);
  assert.ok(answer.content);
  assert.ok(answer.content.length > 0);
});
