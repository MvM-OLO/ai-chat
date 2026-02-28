package org.example.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MiniMax API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minimax")
public class MiniMaxConfig {

    /**
     * API Key (在 MiniMax 开放平台获取)
     */
    private String apiKey;

    /**
     * API Base URL
     */
    private String baseUrl = "https://api.minimaxi.com/v1";

    /**
     * 模型名称
     */
    private String model = "MiniMax-M2.5";
}
