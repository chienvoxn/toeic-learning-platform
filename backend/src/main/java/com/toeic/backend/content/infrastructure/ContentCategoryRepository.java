package com.toeic.backend.content.infrastructure;

import com.toeic.backend.content.domain.ContentCategory;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ContentCategoryRepository extends Repository<ContentCategory, Integer> {
    Optional<ContentCategory> findByCode(String code);
}
