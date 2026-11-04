const sections = [
  [
    "movies",
    "Movie short clip",
    157,
    "Phim",
    [
      ["PRINCESS MONONOKE…", "01:04", "A2", true],
      ["KIKI’S DELIVERY SERV…", "00:50", "B1"],
    ],
  ],
  [
    "conversation",
    "Daily English Conversation",
    148,
    "Hội thoại",
    [
      ["Love mom", "02:24", "A2"],
      ["Valentine’s Day Story | …", "02:59", "A2"],
    ],
  ],
  [
    "toeic",
    "TOEIC LISTENING",
    80,
    "TOEIC",
    [
      ["Test 1 Part 1", "08:21", "A2"],
      ["Test 2 Part 1", "08:34", "B1"],
    ],
  ],
  [
    "entertainment",
    "Entertainment",
    39,
    "Luyện nghe",
    [
      ["SƠN TÙNG MTP × TY…", "04:18", "B1"],
      ["Discover Tet: Exploring …", "03:06", "B2", true],
    ],
  ],
  [
    "kid",
    "Kid",
    14,
    "Luyện nghe",
    [
      ["English for Kids", "02:34", "A1"],
      ["Learning together", "01:46", "A1"],
    ],
  ],
  [
    "songs",
    "US UK SONGS",
    71,
    "Bài hát",
    [
      ["Alan Walker - Faded", "03:33", "B1", true],
      ["I love you 300 - Steph…", "03:21", "A2"],
    ],
  ],
  [
    "bbc",
    "BBC LEARNING ENGLISH",
    105,
    "Tin tức",
    [
      ["Morning Routine: Pha…", "02:34", "B2"],
      ["4 slang words: English…", "00:57", "B1", true],
    ],
  ],
  [
    "science",
    "Science and Facts",
    62,
    "Khoa học",
    [
      ["The science of everyday life", "03:28", "B2"],
      ["Amazing facts", "02:47", "B1"],
    ],
  ],
  [
    "stories",
    "Fairy Tales",
    30,
    "Truyện",
    [
      ["The Goose that Laid …", "01:24", "B1", true],
      ["Jack and Beanstalk…", "03:25", "A2"],
    ],
  ],
  [
    "podcast",
    "Podcast",
    18,
    "Podcast",
    [
      ["A fun way to introduce…", "00:58", "B1"],
      ["Who I’d like to meet?", "01:05", "B1", true],
    ],
  ],
  [
    "anime",
    "Anime",
    256,
    "Anime",
    [
      ["Attack on Titan", "02:38:58", "B2"],
      ["Naruto ss1 ep 1", "24:25", "B2"],
    ],
  ],
].map(([id, title, total, cat, items], sectionIndex) => ({
  id,
  title,
  total,
  cat,
  items: items.map(([title, duration, diff, pro], i) => ({
    id: `${id}-${i}`,
    section: id,
    title,
    duration,
    diff,
    pro: !!pro,
    cat,
    part: id === "toeic" ? "Part 1" : "",
    tagColor:
      sectionIndex % 3 === 0
        ? i
          ? "#6C382C"
          : "#2D4841"
        : i
          ? "#F9CCEB"
          : "#FFF27F",
  })),
}));
const contents = sections.flatMap((s) => s.items);
export const contentRepository = {
  getCategories: () => ["Tất cả", ...new Set(sections.map((s) => s.cat))],
  getContents: () => contents.map((i) => ({ ...i })),
  getSections: () => sections.map(({ items, ...s }) => ({ ...s })),
};
export function filterContents(items, category, search) {
  const query = search.trim().toLowerCase();
  return items.filter(
    (i) =>
      (category === "Tất cả" || i.cat === category) &&
      (!query || i.title.toLowerCase().includes(query)),
  );
}
