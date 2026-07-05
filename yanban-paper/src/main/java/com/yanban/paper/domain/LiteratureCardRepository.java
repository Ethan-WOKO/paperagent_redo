package com.yanban.paper.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiteratureCardRepository extends JpaRepository<LiteratureCard, Long> {
    Optional<LiteratureCard> findByDoi(String doi);

    Optional<LiteratureCard> findByArxivId(String arxivId);

    Optional<LiteratureCard> findByOpenAlexId(String openAlexId);

    Optional<LiteratureCard> findByS2Id(String s2Id);

    Optional<LiteratureCard> findFirstByTitleHash(String titleHash);

    @Query("""
            select c
            from LiteratureCard c
            where lower(c.title) like lower(concat('%', :keyword, '%'))
            order by coalesce(c.citationCount, 0) desc,
                     coalesce(c.publicationYear, 0) desc,
                     c.updatedAt desc
            """)
    List<LiteratureCard> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
