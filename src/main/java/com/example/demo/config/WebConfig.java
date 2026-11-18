package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.demo.model.service.FileStore;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final FileStore fileStore;

    public WebConfig(FileStore fileStore) {
        this.fileStore = fileStore;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(fileStore.getRoot().toUri().toString());
    }
}
