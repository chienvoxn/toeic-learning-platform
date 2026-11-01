import { useState, useEffect } from 'react';
import { contentRepo } from '../repositories/content.repo';

export function useContentCatalog() {
  const [contents, setContents] = useState([]);
  const [categories, setCategories] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('Tất cả');
  const [selectedDifficulty, setSelectedDifficulty] = useState('Tất cả');
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    contentRepo.getCategories().then((cats) => {
      if (active) setCategories(cats);
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');

    contentRepo
      .getContents({
        category: selectedCategory,
        difficulty: selectedDifficulty,
        search: searchQuery,
      })
      .then((data) => {
        if (active) setContents(data);
      })
      .catch((err) => {
        if (active) setError(err.message || 'Không thể tải danh mục học liệu.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [selectedCategory, selectedDifficulty, searchQuery]);

  const toggleBookmark = async (id) => {
    try {
      const updated = await contentRepo.toggleBookmark(id);
      setContents((prev) => prev.map((item) => (item.id === id ? updated : item)));
    } catch (err) {
      console.error('Lỗi khi bookmark:', err);
    }
  };

  return {
    contents,
    categories,
    selectedCategory,
    setSelectedCategory,
    selectedDifficulty,
    setSelectedDifficulty,
    searchQuery,
    setSearchQuery,
    loading,
    error,
    toggleBookmark,
  };
}
