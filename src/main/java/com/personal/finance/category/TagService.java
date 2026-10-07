package com.personal.finance.category;

import com.personal.finance.category.dto.TagRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for transaction tags.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional
    public Tag create(TagRequest request) {
        if (tagRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "A tag with this name already exists");
        }
        Tag tag = new Tag();
        tag.setName(request.name().trim());
        return tagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public List<Tag> list() {
        return tagRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tag not found"));
        tagRepository.delete(tag);
    }
}
