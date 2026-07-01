package com.tuoman.ai_task_orchestrator.embedding;

import com.tuoman.ai_task_orchestrator.repository.EmbeddingCacheMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EmbeddingCacheServiceMetricsIntegrationTest {

    private static final String CONTENT = "metrics integration chunk content";

    @Autowired
    private EmbeddingCacheService embeddingCacheService;

    @Autowired
    private EmbeddingCacheMetricRepository embeddingCacheMetricRepository;

    @Test
    void shouldRecordHitMissWriteAndConflictThroughCacheFlow() {
        MockEmbeddingClient provider = new MockEmbeddingClient();

        CachedEmbeddingResult miss = embeddingCacheService.getOrCompute(
                CONTENT,
                provider.provider(),
                provider.model(),
                provider.dimension(),
                provider
        );
        CachedEmbeddingResult hit = embeddingCacheService.getOrCompute(
                CONTENT,
                provider.provider(),
                provider.model(),
                provider.dimension(),
                provider
        );

        assertThat(miss.cacheHit()).isFalse();
        assertThat(hit.cacheHit()).isTrue();

        var metric = embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                provider.provider(),
                provider.model(),
                provider.dimension()
        ).orElseThrow();

        assertThat(metric.getMissCount()).isEqualTo(1L);
        assertThat(metric.getProviderCallCount()).isEqualTo(1L);
        assertThat(metric.getWriteCount()).isEqualTo(1L);
        assertThat(metric.getHitCount()).isEqualTo(1L);
        assertThat(metric.getSavedProviderCallCount()).isEqualTo(1L);
        assertThat(metric.getConflictCount()).isZero();
    }
}
