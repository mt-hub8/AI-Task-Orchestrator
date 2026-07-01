package com.tuoman.ai_task_orchestrator.embedding;

import com.tuoman.ai_task_orchestrator.dto.EmbeddingCacheMetricItemResponse;
import com.tuoman.ai_task_orchestrator.dto.EmbeddingCacheMetricsResponse;
import com.tuoman.ai_task_orchestrator.repository.EmbeddingCacheMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Transactional
class EmbeddingCacheMetricsServiceIntegrationTest {

    @Autowired
    private EmbeddingCacheMetricsService embeddingCacheMetricsService;

    @Autowired
    private EmbeddingCacheMetricRepository embeddingCacheMetricRepository;

    @Test
    void shouldRecordHitAndSavedProviderCallCount() {
        embeddingCacheMetricsService.recordHit(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);
        embeddingCacheMetricsService.recordHit(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);

        EmbeddingCacheMetricItemResponse item = singleItem();
        assertThat(item.getHitCount()).isEqualTo(2L);
        assertThat(item.getSavedProviderCallCount()).isEqualTo(2L);
        assertThat(item.getHitRate()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordMissAndProviderCallCount() {
        embeddingCacheMetricsService.recordMiss(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);

        EmbeddingCacheMetricItemResponse item = singleItem();
        assertThat(item.getMissCount()).isEqualTo(1L);
        assertThat(item.getProviderCallCount()).isEqualTo(1L);
        assertThat(item.getWriteCount()).isZero();
        assertThat(item.getHitRate()).isZero();
    }

    @Test
    void shouldRecordWriteAfterMiss() {
        embeddingCacheMetricsService.recordMiss(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);
        embeddingCacheMetricsService.recordWrite(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);

        EmbeddingCacheMetricItemResponse item = singleItem();
        assertThat(item.getMissCount()).isEqualTo(1L);
        assertThat(item.getWriteCount()).isEqualTo(1L);
    }

    @Test
    void shouldRecordConflictCount() {
        embeddingCacheMetricsService.recordConflict(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);

        assertThat(singleItem().getConflictCount()).isEqualTo(1L);
    }

    @Test
    void shouldCalculateHitRateCorrectly() {
        embeddingCacheMetricsService.recordHit(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);
        embeddingCacheMetricsService.recordHit(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);
        embeddingCacheMetricsService.recordHit(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);
        embeddingCacheMetricsService.recordMiss(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, 128);

        assertThat(singleItem().getHitRate()).isEqualTo(0.75);
    }

    @Test
    void shouldReturnZeroHitRateWhenNoLookups() {
        EmbeddingCacheMetricItemResponse item = new EmbeddingCacheMetricItemResponse(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                128,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                EmbeddingCacheMetricsService.calculateHitRate(0L, 0L)
        );

        assertThat(item.getHitRate()).isZero();
    }

    @Test
    void shouldTrackDifferentEmbeddingSpacesSeparately() {
        embeddingCacheMetricsService.recordHit("provider-a", "model-a", 128);
        embeddingCacheMetricsService.recordMiss("provider-b", "model-b", 256);

        EmbeddingCacheMetricsResponse response = embeddingCacheMetricsService.getMetrics();
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems())
                .anySatisfy(item -> {
                    assertThat(item.getProvider()).isEqualTo("provider-a");
                    assertThat(item.getHitCount()).isEqualTo(1L);
                })
                .anySatisfy(item -> {
                    assertThat(item.getProvider()).isEqualTo("provider-b");
                    assertThat(item.getMissCount()).isEqualTo(1L);
                });
    }

    @Test
    void shouldReturnEmptyItemsWhenNoMetrics() {
        EmbeddingCacheMetricsResponse response = embeddingCacheMetricsService.getMetrics();
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    void shouldNotPropagateMetricUpdateFailure() {
        embeddingCacheMetricRepository.deleteAll();

        assertThatCode(() -> embeddingCacheMetricsService.recordHit(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                128
        )).doesNotThrowAnyException();
    }

    private EmbeddingCacheMetricItemResponse singleItem() {
        return embeddingCacheMetricsService.getMetrics().getItems().getFirst();
    }
}
