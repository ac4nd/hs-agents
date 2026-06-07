package com.hypersense.boot.framework.agents.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智谱 Embedding API 客户端（RestTemplate 实现，避免 zai-sdk Jackson 版本冲突）
 *
 * @author Claude
 * @since 2026/5/29
 */
@Slf4j
public class ZhipuEmbeddingClient {

    private static final String DEFAULT_EMBEDDING_PATH = "/embeddings";

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public ZhipuEmbeddingClient(String endpoint, String apiKey, String model) {
        this.restTemplate = new RestTemplate();
        // 标准化 endpoint，确保以 /embeddings 结尾
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.endpoint = base + DEFAULT_EMBEDDING_PATH;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * 批量向量化（单次 API 调用）
     *
     * @param texts 待向量化的文本列表
     * @return 对应的向量列表，与输入顺序一致
     */
    @SuppressWarnings("unchecked")
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);

            if (response == null || response.get("data") == null) {
                log.warn("ZhipuEmbeddingClient: API 返回为空");
                return List.of();
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");
            // 按 index 排序确保顺序一致
            dataList.sort((a, b) -> Integer.compare(
                    ((Number) a.get("index")).intValue(),
                    ((Number) b.get("index")).intValue()));

            List<float[]> result = new ArrayList<>();
            for (Map<String, Object> item : dataList) {
                List<Number> vec = (List<Number>) item.get("embedding");
                float[] arr = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    arr[i] = vec.get(i).floatValue();
                }
                result.add(arr);
            }
            return result;
        } catch (Exception e) {
            log.error("ZhipuEmbeddingClient: Embedding API 调用失败", e);
            return List.of();
        }
    }

    /**
     * 单条文本向量化
     */
    public float[] embed(String text) {
        List<float[]> results = batchEmbed(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }
}
