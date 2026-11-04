package com.example.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.demo.model.domain.Post;
import com.example.demo.model.dto.PostForm;
import com.example.demo.model.service.PostService;

@Controller
@RequestMapping("/board")
public class BoardController {
    private final PostService postService;

    public BoardController(PostService postService) {
        this.postService = postService;
    }

    /** 게시글 목록: GET /board */
    @GetMapping
    public String list(Model model) {
        List<Post> posts = postService.findAll();
        model.addAttribute("posts", posts);
        return "board/list";
    }

    /** 내가 쓴 글: GET /board/my (로그인 필요) */
    @GetMapping("/my")
    public String myPosts(Principal principal, Model model) {
        model.addAttribute("posts", postService.findMyPosts(principal.getName()));
        model.addAttribute("listTitle", "내가 쓴 글");
        return "board/list";
    }

    /** 글쓰기 화면: GET /board/write */
    @GetMapping("/write")
    public String writeForm() {
        return "board/form";
    }

    /** 글쓰기 처리: POST /board/write */
    @PostMapping("/write")
    public String write(PostForm postForm, Principal principal, Model model) {
        try {
            Post post = postService.write(postForm, principal.getName());
            return "redirect:/board/" + post.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "board/form";
        }
    }

    /** 게시글 상세: GET /board/{id} */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Post post = postService.read(id);
        model.addAttribute("post", post);
        return "board/detail";
    }

    /** 수정 화면: GET /board/{id}/edit */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Post post = postService.findForEdit(id);
        PostForm postForm = new PostForm();
        postForm.setTitle(post.getTitle());
        postForm.setContent(post.getContent());
        model.addAttribute("postForm", postForm);
        model.addAttribute("postId", id);
        return "board/form";
    }

    /** 수정 처리: POST /board/{id}/edit */
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, PostForm postForm, Model model) {
        try {
            postService.edit(id, postForm);
            return "redirect:/board/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("postId", id);
            return "board/form";
        }
    }

    /** 삭제 처리: POST /board/{id}/delete */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        postService.delete(id);
        return "redirect:/board?deleted";
    }
}
