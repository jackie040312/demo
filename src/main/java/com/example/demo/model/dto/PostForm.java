package com.example.demo.model.dto;

import lombok.Data;

/** 글쓰기·수정 화면에서 제목과 내용만 받는 DTO. */
@Data
public class PostForm {
    private String title;
    private String content;
}
