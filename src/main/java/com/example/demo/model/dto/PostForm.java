package com.example.demo.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/** 작성자와 신청 인원은 서버가 지정하며 폼에서 받지 않는다. */
@Data
public class PostForm {
    private String title;
    private String content;
    private Integer capacity;
    private MultipartFile poster;
}
