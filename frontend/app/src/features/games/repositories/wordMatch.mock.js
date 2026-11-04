const RAW_PAIRS = [
  { id: "1", en: "badminton", vi: "cầu lông" },
  { id: "2", en: "racket", vi: "vợt" },
  { id: "3", en: "net", vi: "lưới" },
  { id: "4", en: "referee whistle", vi: "còi trọng tài" },
  { id: "5", en: "nail", vi: "móng tay" },
  { id: "6", en: "practice", vi: "luyện tập" },
];
export const getWordPairs = () => RAW_PAIRS.map((p) => ({ ...p }));
