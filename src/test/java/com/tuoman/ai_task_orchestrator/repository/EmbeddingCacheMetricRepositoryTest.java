package com.tuoman.ai_task_orchestrator.repository;

import com.tuoman.ai_task_orchestrator.entity.EmbeddingCacheMetricEntity;
import com.tuoman.ai_task_orchestrator.embedding.MockEmbeddingClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EmbeddingCacheMetricRepositoryTest {

    @Autowired
    private EmbeddingCacheMetricRepository embeddingCacheMetricRepository;

    @Test
    void shouldCreateMetricRow() {
        EmbeddingCacheMetricEntity entity = saveMetric(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        );

        assertThat(entity.getId()).isNotNull();
        assertThat(embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        )).isPresent();
    }

    @Test
    void shouldIncrementHitCountAtomically() {
        saveMetric(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, MockEmbeddingClient.DIMENSION);

        int updated = embeddingCacheMetricRepository.incrementHit(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                LocalDateTime.now()
        );

        EmbeddingCacheMetricEntity loaded = embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        ).orElseThrow();

        assertThat(updated).isEqualTo(1);
        assertThat(loaded.getHitCount()).isEqualTo(1L);
        assertThat(loaded.getSavedProviderCallCount()).isEqualTo(1L);
    }

    @Test
    void shouldIncrementMissCountAtomically() {
        saveMetric(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, MockEmbeddingClient.DIMENSION);

        embeddingCacheMetricRepository.incrementMiss(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                LocalDateTime.now()
        );

        EmbeddingCacheMetricEntity loaded = embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        ).orElseThrow();

        assertThat(loaded.getMissCount()).isEqualTo(1L);
        assertThat(loaded.getProviderCallCount()).isEqualTo(1L);
    }

    @Test
    void shouldIncrementWriteCountAtomically() {
        saveMetric(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, MockEmbeddingClient.DIMENSION);

        embeddingCacheMetricRepository.incrementWrite(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                LocalDateTime.now()
        );

        assertThat(embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        ).orElseThrow().getWriteCount()).isEqualTo(1L);
    }

    @Test
    void shouldIncrementConflictCountAtomically() {
        saveMetric(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, MockEmbeddingClient.DIMENSION);

        embeddingCacheMetricRepository.incrementConflict(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION,
                LocalDateTime.now()
        );

        assertThat(embeddingCacheMetricRepository.findByProviderAndModelAndDimension(
                MockEmbeddingClient.PROVIDER,
                MockEmbeddingClient.DEFAULT_MODEL,
                MockEmbeddingClient.DIMENSION
        ).orElseThrow().getConflictCount()).isEqualTo(1L);
    }

    @Test
    void shouldRejectDuplicateEmbeddingSpace() {
        saveMetric(MockEmbeddingClient.PROVIDER, MockEmbeddingClient.DEFAULT_MODEL, MockEmbeddingClient.DIMENSION);

        EmbeddingCacheMetricEntity duplicate = new EmbeddingCacheMetricEntity();
        duplicate.setProvider(MockEmbeddingClient.PROVIDER);
        duplicate.setModel(MockEmbeddingClient.DEFAULT_MODEL);
        duplicate.setDimension(MockEmbeddingClient.DIMENSION);

        assertThatThrownBy(() -> embeddingCacheMetricRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldTrackDifferentEmbeddingSpacesSeparately() {
        saveMetric("provider-a", "model-a", 128);
        saveMetric("provider-b", "model-b", 256);

        embeddingCacheMetricRepository.incrementHit("provider-a", "model-a", 128, LocalDateTime.now());
        embeddingCacheMetricRepository.incrementMiss("provider-b", "model-b", 256, LocalDateTime.now());

        assertThat(embeddingCacheMetricRepository.findByProviderAndModelAndDimension("provider-a", "model-a", 128)
                .orElseThrow().getHitCount()).isEqualTo(1L);
        assertThat(embeddingCacheMetricRepository.findByProviderAndModelAndDimension("provider-b", "model-b", 256)
                .orElseThrow().getMissCount()).isEqualTo(1L);
    }

    private EmbeddingCacheMetricEntity saveMetric(String provider, String model, int dimension) {
        EmbeddingCacheMetricEntity entity = new EmbeddingCacheMetricEntity();
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setDimension(dimension);
        return embeddingCacheMetricRepository.saveAndFlush(entity);
    }
}
