package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 网页获取工具
 * 
 * 用于获取网页内容，支持：
 * - GET 请求
 * - HTML/Markdown 输出
 * - 超时控制
 * 
 * 学习要点：HTTP 客户端使用、响应处理
 * 参考设计：Claude Code WebFetch
 */
public class WebFetchTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    
    private final HttpClient httpClient;
    
    public WebFetchTool() {
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
    
    @Override
    public String getName() {
        return "WebFetch";
    }
    
    @Override
    public String getDescription() {
        return "获取网页内容，返回 HTML 或 Markdown 格式";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ALLOW;  // 只读操作，自动允许
    }
    
    @Override
    public Optional<String> getDeprecatedName() {
        return Optional.empty();  // 无废弃名称
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode urlProp = properties.putObject("url");
        urlProp.put("type", "string");
        urlProp.put("description", "要获取的网页 URL");
        
        ObjectNode timeoutProp = properties.putObject("timeout");
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时时间（秒，默认 30）");
        
        schema.putArray("required").add("url");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String urlStr = (String) params.get("url");
            if (urlStr == null || urlStr.isEmpty()) {
                return ToolResult.failure("参数 'url' 是必需的");
            }
            
            // 验证 URL
            URI uri;
            try {
                uri = URI.create(urlStr);
            } catch (Exception e) {
                return ToolResult.failure("无效的 URL: " + urlStr);
            }
            
            // 超时设置
            Integer timeoutSec = (Integer) params.get("timeout");
            Duration timeout = timeoutSec != null 
                ? Duration.ofSeconds(timeoutSec) 
                : DEFAULT_TIMEOUT;
            
            // 构建请求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("User-Agent", "CodeMind/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .GET()
                .build();
            
            // 发送请求
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
            
            // 检查响应状态
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                return ToolResult.failure("HTTP 错误: " + statusCode + " " + response.uri());
            }
            
            String content = response.body();
            
            // 简单清理 HTML 标签（实际应该用更专业的库）
            String cleaned = cleanHtml(content);
            
            return ToolResult.success(cleaned);
            
        } catch (java.net.http.HttpTimeoutException e) {
            return ToolResult.failure("请求超时: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.failure("获取网页失败: " + e.getMessage());
        }
    }
    
    /**
     * 简单清理 HTML 标签
     * 
     * 注意：这是一个简化实现。生产环境应该使用 JSoup 或 similar 库。
     */
    private String cleanHtml(String html) {
        if (html == null) {
            return "";
        }
        
        // 移除 script 和 style 标签及其内容
        String cleaned = html.replaceAll("(?s)<script[^>]*>.*?</script>", "");
        cleaned = cleaned.replaceAll("(?s)<style[^>]*>.*?</style>", "");
        
        // 移除 HTML 注释
        cleaned = cleaned.replaceAll("<!--.*?-->", "");
        
        // 将 <br> 转换为换行
        cleaned = cleaned.replaceAll("<br[^>]*>", "\n");
        
        // 移除普通 HTML 标签
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        
        // 解码 HTML 实体
        cleaned = cleaned.replaceAll("&nbsp;", " ");
        cleaned = cleaned.replaceAll("&lt;", "<");
        cleaned = cleaned.replaceAll("&gt;", ">");
        cleaned = cleaned.replaceAll("&amp;", "&");
        cleaned = cleaned.replaceAll("&quot;", "\"");
        cleaned = cleaned.replaceAll("&#39;", "'");
        
        // 清理多余空白
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
}
