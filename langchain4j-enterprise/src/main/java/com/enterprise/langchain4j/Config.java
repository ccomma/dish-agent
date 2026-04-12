package com.enterprise.langchain4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置加载工具类
 * 从 config.properties 文件加载配置
 */
public class Config {

    private static final String CONFIG_FILE = "config.properties";
    private static Config instance;
    private final Properties properties;

    private Config() {
        properties = new Properties();
        loadConfig();
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                // 尝试从文件系统加载
                java.io.FileInputStream fis = new java.io.FileInputStream(CONFIG_FILE);
                properties.load(fis);
                fis.close();
            } else {
                properties.load(input);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法加载配置文件 " + CONFIG_FILE + ": " + e.getMessage(), e);
        }
    }

    public String getApiKey() {
        String apiKey = properties.getProperty("MINIMAX_API_KEY");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_api_key_here")) {
            throw new IllegalStateException(
                "请在 config.properties 中设置有效的 MINIMAX_API_KEY\n" +
                "示例: MINIMAX_API_KEY=your_actual_api_key_here"
            );
        }
        return apiKey;
    }

    public String getBaseUrl() {
        return properties.getProperty("MINIMAX_BASE_URL", "https://api.minimax.chat/v1");
    }

    public String getModel() {
        return properties.getProperty("MINIMAX_MODEL", "minimax-m2.7");
    }

    public String getVectorStoreType() {
        return properties.getProperty("VECTOR_STORE_TYPE", "inmemory");
    }

    public String getMilvusHost() {
        return properties.getProperty("MILVUS_HOST", "localhost");
    }

    public int getMilvusPort() {
        return Integer.parseInt(properties.getProperty("MILVUS_PORT", "19530"));
    }

    public String getMilvusCollection() {
        return properties.getProperty("MILVUS_COLLECTION", "dish_knowledge");
    }

    public String getEmbeddingModel() {
        return properties.getProperty("EMBEDDING_MODEL", "text-embedding-3-small");
    }

    public int getEmbeddingDimension() {
        return Integer.parseInt(properties.getProperty("EMBEDDING_DIMENSION", "1536"));
    }

    public String getCohereApiKey() {
        String apiKey = properties.getProperty("COHERE_API_KEY");
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("your_cohere_api_key_here")) {
            return null;
        }
        return apiKey;
    }
}
