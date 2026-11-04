import { useState } from "react";
import {
  contentRepository,
  filterContents,
} from "../repositories/content.mock";

export default function useContentCatalog() {
  const [selectedCat, setSelectedCat] = useState("Tất cả");
  const [search, setSearch] = useState("");
  const categories = contentRepository.getCategories();
  const filtered = filterContents(
    contentRepository.getContents(),
    selectedCat,
    search,
  );

  const sections = contentRepository.getSections();
  return {
    selectedCat,
    setSelectedCat,
    search,
    setSearch,
    categories,
    filtered,
    sections,
  };
}
