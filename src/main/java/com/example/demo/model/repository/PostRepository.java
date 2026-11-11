package com.example.demo.model.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.model.domain.Post;
import com.example.demo.model.domain.Member;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByAuthor_Username(String username, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.title LIKE %:keyword% OR p.content LIKE %:keyword%")
    Page<Post> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.author.name LIKE %:name%")
    Page<Post> searchByWriter(@Param("name") String name, Pageable pageable);

    boolean existsByAuthor(Member author);
}
