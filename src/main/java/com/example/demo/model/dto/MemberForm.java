package com.example.demo.model.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class MemberForm {
    private String username;
    @ToString.Exclude
    private String password;
    @ToString.Exclude
    private String passwordConfirm;
    private String name;
}
