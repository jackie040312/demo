package com.example.demo.controller;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.model.domain.Post;
import com.example.demo.model.service.EnrollmentService;
import com.example.demo.model.service.PostService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/cart")
public class CartController {
    private final PostService postService;
    private final EnrollmentService enrollmentService;

    public CartController(PostService postService, EnrollmentService enrollmentService) {
        this.postService = postService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public String cart(HttpSession session, Principal principal, Model model) {
        model.addAttribute("cartPosts", postService.findAllById(getCart(session)));
        model.addAttribute("enrollments", enrollmentService.findMine(principal.getName()));
        return "cart";
    }

    @PostMapping("/{postId}")
    public String add(@PathVariable Long postId, HttpSession session) {
        Post post = postService.findById(postId);
        if (!post.isRecruit()) return "redirect:/board/" + postId;
        getCart(session).add(postId);
        return "redirect:/cart";
    }

    @PostMapping("/{postId}/remove")
    public String remove(@PathVariable Long postId, HttpSession session) {
        getCart(session).remove(postId);
        return "redirect:/cart";
    }

    @PostMapping("/confirm")
    public String confirm(HttpSession session, Principal principal, RedirectAttributes redirect) {
        Set<Long> cart = getCart(session);
        try {
            int count = enrollmentService.confirm(principal.getName(), cart);
            cart.clear();
            redirect.addFlashAttribute("message", count + "개 모집글 신청이 확정되었습니다.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getCart(HttpSession session) {
        Set<Long> cart = (Set<Long>) session.getAttribute("cart");
        if (cart == null) {
            cart = new LinkedHashSet<>();
            session.setAttribute("cart", cart);
        }
        return cart;
    }

    @PostMapping("/enrollments/{id}/cancel")
    public String cancel(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            enrollmentService.cancel(id, principal.getName());
            redirect.addFlashAttribute("message", "신청이 취소되었습니다.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }
}
