package com.example.demo.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.demo.model.service.CommentService;

@Controller
@RequestMapping("/board/{postId}/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public String write(@PathVariable Long postId, @RequestParam(defaultValue = "") String content,
            Principal principal) {
        try {
            commentService.write(postId, content, principal.getName());
            return "redirect:/board/" + postId + "#comments";
        } catch (IllegalArgumentException e) {
            return "redirect:/board/" + postId + "?commentError#comments";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long postId, @PathVariable Long id) {
        commentService.delete(postId, id);
        return "redirect:/board/" + postId + "?commentDeleted#comments";
    }
}
