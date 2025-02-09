package io.getunleash.repository;

import io.getunleash.FeatureToggle;
import io.getunleash.Segment;
import io.getunleash.UnleashException;
import io.getunleash.event.EventDispatcher;
import io.getunleash.event.UnleashReady;
import io.getunleash.lang.Nullable;
import io.getunleash.util.UnleashConfig;
import io.getunleash.util.UnleashScheduledExecutor;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FeatureRepository implements IFeatureRepository {

    private final UnleashConfig unleashConfig;
    private final FeatureBackupHandlerFile featureBackupHandler;
    private final FeatureBootstrapHandler featureBootstrapHandler;
    private final FeatureFetcher featureFetcher;
    private final EventDispatcher eventDispatcher;

    private FeatureCollection featureCollection;
    private boolean ready;

    public FeatureRepository(UnleashConfig unleashConfig) {
        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = new FeatureBackupHandlerFile(unleashConfig);
        this.featureFetcher = unleashConfig.getUnleashFeatureFetcherFactory().apply(unleashConfig);
        this.featureBootstrapHandler = new FeatureBootstrapHandler(unleashConfig);
        this.eventDispatcher = new EventDispatcher(unleashConfig);

        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    protected FeatureRepository(
            UnleashConfig unleashConfig,
            FeatureBackupHandlerFile featureBackupHandler,
            EventDispatcher eventDispatcher,
            FeatureFetcher featureFetcher,
            FeatureBootstrapHandler featureBootstrapHandler) {
        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.featureFetcher = featureFetcher;
        this.featureBootstrapHandler = featureBootstrapHandler;
        this.eventDispatcher = eventDispatcher;
        this.initCollections(unleashConfig.getScheduledExecutor());
    }

    protected FeatureRepository(
            UnleashConfig unleashConfig,
            FeatureBackupHandlerFile featureBackupHandler,
            UnleashScheduledExecutor executor,
            FeatureFetcher featureFetcher,
            FeatureBootstrapHandler featureBootstrapHandler) {
        this.unleashConfig = unleashConfig;
        this.featureBackupHandler = featureBackupHandler;
        this.featureFetcher = featureFetcher;
        this.featureBootstrapHandler = featureBootstrapHandler;
        this.eventDispatcher = new EventDispatcher(unleashConfig);
        this.initCollections(executor);
    }

    private void initCollections(UnleashScheduledExecutor executor) {
        this.featureCollection = this.featureBackupHandler.read();
        if (this.featureCollection.getToggleCollection().getFeatures().isEmpty()) {
            this.featureCollection = this.featureBootstrapHandler.read();
        }

        if (unleashConfig.isSynchronousFetchOnInitialisation()) {
            updateFeatures().run();
        }

        executor.setInterval(updateFeatures(), 0, unleashConfig.getFetchTogglesInterval());
    }

    private Runnable updateFeatures() {
        return () -> {
            try {
                ClientFeaturesResponse response = featureFetcher.fetchFeatures();
                eventDispatcher.dispatch(response);
                if (response.getStatus() == ClientFeaturesResponse.Status.CHANGED) {
                    SegmentCollection segmentCollection = response.getSegmentCollection();
                    featureCollection =
                            new FeatureCollection(
                                    response.getToggleCollection(),
                                    segmentCollection != null
                                            ? segmentCollection
                                            : new SegmentCollection(Collections.emptyList()));

                    featureBackupHandler.write(featureCollection);
                }

                if (!ready) {
                    eventDispatcher.dispatch(new UnleashReady());
                    ready = true;
                }
            } catch (UnleashException e) {
                eventDispatcher.dispatch(e);
            }
        };
    }

    @Override
    public @Nullable FeatureToggle getToggle(String name) {
        return featureCollection.getToggleCollection().getToggle(name);
    }

    @Override
    public List<String> getFeatureNames() {
        return featureCollection.getToggleCollection().getFeatures().stream()
                .map(FeatureToggle::getName)
                .collect(Collectors.toList());
    }

    @Override
    public Segment getSegment(Integer id) {
        return featureCollection.getSegmentCollection().getSegment(id);
    }
}
