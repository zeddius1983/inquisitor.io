# Spring AI 2.x

## Dependencies (Gradle Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0-RC1"))

    // Choose one or more model providers:
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    // implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    // implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    // RAG / vector store (optional):
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
}
```

## ChatClient — Fluent API (Recommended Entry Point)

```java
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultSystem("You are a helpful assistant.")
            .build();
    }
}

@Service
@RequiredArgsConstructor
public class AssistantService {

    private final ChatClient chatClient;

    // Plain text response
    public String ask(String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }

    // Streaming response
    public Flux<String> stream(String question) {
        return chatClient.prompt()
            .user(question)
            .stream()
            .content();
    }
}
```

## Structured Output

Map the model response directly to a Java record — no manual parsing:

```java
record ProductRecommendation(String name, String reason, double confidence) {}
record RecommendationList(List<ProductRecommendation> recommendations) {}

public RecommendationList recommend(String userProfile) {
    return chatClient.prompt()
        .user(u -> u.text("Recommend products for: {profile}").param("profile", userProfile))
        .call()
        .entity(RecommendationList.class);
}
```

## Tool Calling

Annotate a service method with `@Tool` — Spring AI handles the call/response loop:

```java
@Component
public class WeatherTools {

    @Tool(description = "Get current weather for a city")
    public WeatherData getWeather(
        @ToolParam(description = "City name") String city
    ) {
        // call real weather API here
        return new WeatherData(city, 22.5, "Sunny");
    }

    record WeatherData(String city, double tempCelsius, String condition) {}
}

// Usage — Spring AI automatically invokes tools when the model requests them
@Service
@RequiredArgsConstructor
public class WeatherAssistant {

    private final ChatClient chatClient;
    private final WeatherTools weatherTools;

    public String answer(String question) {
        return chatClient.prompt()
            .user(question)
            .tools(weatherTools)
            .call()
            .content();
    }
}
```

## Advisors — Composable Cross-Cutting Concerns

Advisors intercept the prompt/response lifecycle (similar to servlet filters):

```java
@Bean
public ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore) {
    return ChatClient.builder(chatModel)
        .defaultSystem("You are a helpful support agent.")
        .defaultAdvisors(
            // RAG: augment prompt with relevant documents from the vector store
            QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.defaults().withTopK(5))
                .build(),
            // Conversation memory: maintain chat history across turns
            MessageChatMemoryAdvisor.builder(new InMemoryChatMemory()).build()
        )
        .build();
}
```

### RAG with QuestionAnswerAdvisor

```java
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final ChatClient chatClient;

    public String query(String question, String conversationId) {
        return chatClient.prompt()
            .user(question)
            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
            .call()
            .content();
    }
}
```

### Ingesting Documents into the Vector Store

```java
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public void ingest(List<String> texts, Map<String, Object> metadata) {
        val documents = texts.stream()
            .map(t -> new Document(t, metadata))
            .toList();
        vectorStore.add(documents);
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK).build()
        );
    }
}
```

## PgVector Store Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536

  datasource:
    url: jdbc:postgresql://localhost:5432/example
```

```java
@Bean
public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .dimensions(1536)
        .build();
}
```

## Conversation Memory Patterns

```java
// In-memory (single node, dev/test)
new InMemoryChatMemory()

// JDBC-backed (production, multi-node)
@Bean
public ChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
    return new JdbcChatMemory(jdbcTemplate);
}

// Use with advisor:
MessageChatMemoryAdvisor.builder(chatMemory)
    .conversationIdSupplier(() -> requestContext.getConversationId())
    .build()
```

## MCP Tool Integration

```java
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class McpChatController {

    private final ChatModel chatModel;
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    @PostMapping
    public String chat(@RequestBody String message) {
        return ChatClient.create(chatModel)
            .prompt(message)
            .tools(toolCallbackProvider.getToolCallbacks())
            .call()
            .content();
    }
}
```

## Testing AI Components

```java
@SpringBootTest
class AssistantServiceTest {

    @MockBean
    ChatModel chatModel;

    @Autowired
    AssistantService assistantService;

    @Test
    void shouldReturnModelResponse() {
        when(chatModel.call(any(Prompt.class)))
            .thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("42"))
            )));

        assertThat(assistantService.ask("What is the answer?")).isEqualTo("42");
    }
}
```

## Quick Reference

| API | Purpose |
|---|---|
| `ChatClient` | Primary fluent interface for chat interactions |
| `.call().content()` | Synchronous plain-text response |
| `.stream().content()` | Streaming `Flux<String>` response |
| `.call().entity(MyRecord.class)` | Structured output mapped to a record |
| `@Tool` | Annotate a method as a callable tool |
| `@ToolParam` | Describe a tool parameter for the model |
| `QuestionAnswerAdvisor` | RAG — augments prompts with vector search results |
| `MessageChatMemoryAdvisor` | Maintains conversation history across turns |
| `VectorStore` | Add / similarity-search documents |
| `EmbeddingModel` | Convert text to vector embeddings |
| `SyncMcpToolCallbackProvider` | Expose MCP server tools to the chat model |
