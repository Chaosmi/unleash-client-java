package io.getunleash;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.getunleash.repository.ToggleRepository;
import io.getunleash.strategy.Strategy;
import io.getunleash.strategy.UserWithIdStrategy;
import io.getunleash.util.UnleashConfig;
import io.getunleash.variant.Payload;
import io.getunleash.variant.VariantDefinition;
import java.util.*;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UnleashTest {

    private ToggleRepository toggleRepository;
    private UnleashContextProvider contextProvider;
    private Unleash unleash;

    @BeforeEach
    public void setup() {
        toggleRepository = mock(ToggleRepository.class);
        contextProvider = mock(UnleashContextProvider.class);
        when(contextProvider.getContext()).thenReturn(UnleashContext.builder().build());

        UnleashConfig config =
                new UnleashConfig.Builder()
                        .appName("test")
                        .unleashAPI("http://localhost:4242/api/")
                        .environment("test")
                        .unleashContextProvider(contextProvider)
                        .build();

        unleash = new DefaultUnleash(config, toggleRepository, new UserWithIdStrategy());
    }

    @Test
    public void known_toogle_and_strategy_should_be_active() {
        when(toggleRepository.getToggle("test"))
                .thenReturn(
                        new FeatureToggle(
                                "test", true, asList(new ActivationStrategy("default", null))));

        assertThat(unleash.isEnabled("test")).isTrue();
    }

    @Test
    public void unknown_strategy_should_be_considered_inactive() {
        when(toggleRepository.getToggle("test"))
                .thenReturn(
                        new FeatureToggle(
                                "test", true, asList(new ActivationStrategy("whoot_strat", null))));

        assertThat(unleash.isEnabled("test")).isFalse();
    }

    @Test
    public void unknown_feature_should_be_considered_inactive() {
        when(toggleRepository.getToggle("test")).thenReturn(null);

        assertThat(unleash.isEnabled("test")).isFalse();
    }

    @Test
    public void unknown_feature_should_use_default_setting() {
        when(toggleRepository.getToggle("test")).thenReturn(null);

        assertThat(unleash.isEnabled("test", true)).isTrue();
    }

    @Test
    public void fallback_function_should_be_invoked_and_return_true() {
        when(toggleRepository.getToggle("test")).thenReturn(null);
        BiFunction<String, UnleashContext, Boolean> fallbackAction = mock(BiFunction.class);
        when(fallbackAction.apply(eq("test"), any(UnleashContext.class))).thenReturn(true);

        assertThat(unleash.isEnabled("test", fallbackAction)).isTrue();
        verify(fallbackAction, times(1)).apply(anyString(), any(UnleashContext.class));
    }

    @Test
    public void fallback_function_should_be_invoked_also_with_context() {
        when(toggleRepository.getToggle("test")).thenReturn(null);
        BiFunction<String, UnleashContext, Boolean> fallbackAction = mock(BiFunction.class);
        when(fallbackAction.apply(eq("test"), any(UnleashContext.class))).thenReturn(true);

        UnleashContext context = UnleashContext.builder().userId("123").build();

        assertThat(unleash.isEnabled("test", context, fallbackAction)).isTrue();
        verify(fallbackAction, times(1)).apply(anyString(), any(UnleashContext.class));
    }

    @Test
    void fallback_function_should_be_invoked_and_return_false() {
        when(toggleRepository.getToggle("test")).thenReturn(null);
        BiFunction<String, UnleashContext, Boolean> fallbackAction = mock(BiFunction.class);
        when(fallbackAction.apply(eq("test"), any(UnleashContext.class))).thenReturn(false);

        assertThat(unleash.isEnabled("test", fallbackAction)).isFalse();
        verify(fallbackAction, times(1)).apply(anyString(), any(UnleashContext.class));
    }

    @Test
    void fallback_function_should_not_be_called_when_toggle_is_defined() {
        when(toggleRepository.getToggle("test"))
                .thenReturn(
                        new FeatureToggle(
                                "test", true, asList(new ActivationStrategy("default", null))));

        BiFunction<String, UnleashContext, Boolean> fallbackAction = mock(BiFunction.class);
        when(fallbackAction.apply(eq("test"), any(UnleashContext.class))).thenReturn(false);

        assertThat(unleash.isEnabled("test", fallbackAction)).isTrue();
        verify(fallbackAction, never()).apply(anyString(), any(UnleashContext.class));
    }

    @Test
    public void should_register_custom_strategies() {
        // custom strategy
        Strategy customStrategy = mock(Strategy.class);
        when(customStrategy.getName()).thenReturn("custom");

        // register custom strategy
        UnleashConfig config =
                new UnleashConfig.Builder()
                        .appName("test")
                        .unleashAPI("http://localhost:4242/api/")
                        .build();
        unleash = new DefaultUnleash(config, toggleRepository, customStrategy);
        when(toggleRepository.getToggle("test"))
                .thenReturn(
                        new FeatureToggle(
                                "test", true, asList(new ActivationStrategy("custom", null))));

        unleash.isEnabled("test");

        verify(customStrategy, times(1))
                .isEnabled(isNull(), any(UnleashContext.class), any(List.class));
    }

    @Test
    public void should_support_multiple_strategies() {
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        ActivationStrategy activeStrategy = new ActivationStrategy("default", null);

        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy1, activeStrategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test")).isTrue();
    }

    @Test
    public void should_support_context_provider() {
        UnleashContext context = UnleashContext.builder().userId("111").build();
        when(contextProvider.getContext()).thenReturn(context);

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(strategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test")).isTrue();
    }

    @Test
    public void should_support_context_as_part_of_is_enabled_call() {
        UnleashContext context = UnleashContext.builder().userId("13").build();

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 13");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(strategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test", context)).isTrue();
    }

    @Test
    public void should_support_context_as_part_of_is_enabled_call_and_use_default() {
        UnleashContext context = UnleashContext.builder().userId("13").build();

        // Set up a toggle using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 13");

        assertThat(unleash.isEnabled("test", context, true)).isTrue();
    }

    @Test
    public void inactive_feature_toggle() {
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        FeatureToggle featureToggle = new FeatureToggle("test", false, asList(strategy1));
        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test")).isFalse();
    }

    @Test
    public void should_return_known_feature_toggle_definition() {
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        FeatureToggle featureToggle = new FeatureToggle("test", false, asList(strategy1));
        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(((DefaultUnleash) unleash).getFeatureToggleDefinition("test"))
                .hasValue(featureToggle);
    }

    @Test
    public void should_return_empty_for_unknown_feature_toggle_definition() {
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        FeatureToggle featureToggle = new FeatureToggle("test", false, asList(strategy1));
        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(((DefaultUnleash) unleash).getFeatureToggleDefinition("another toggleName"))
                .isEmpty();
    }

    @Test
    public void get_feature_names_should_return_list_of_feature_names() {
        when(toggleRepository.getFeatureNames())
                .thenReturn(asList("toggleFeatureName1", "toggleFeatureName2"));
        assertThat(unleash.getFeatureToggleNames()).hasSize(2);
        assertThat(unleash.getFeatureToggleNames().get(1)).isEqualTo("toggleFeatureName2");
    }

    @Test
    public void get_default_variant_when_disabled() {
        UnleashContext context = UnleashContext.builder().userId("1").build();

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 13");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(strategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result =
                unleash.getVariant("test", context, new Variant("Chuck", "Norris", true));

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Chuck");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("Norris");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void get_default_empty_variant_when_disabled_and_no_default_value_is_specified() {
        UnleashContext context = UnleashContext.builder().userId("1").build();

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 13");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(strategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test", context);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("disabled");
        assertThat(result.getPayload().map(Payload::getValue)).isEmpty();
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    public void get_first_variant() {
        UnleashContext context = UnleashContext.builder().userId("356").build();

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 356");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test", context);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("en");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("en");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void get_second_variant() {
        UnleashContext context = UnleashContext.builder().userId("111").build();

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121, 13");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test", context);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("to");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("to");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void get_disabled_variant_without_context() {

        // Set up a toggleName using UserWithIdStrategy
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy1), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("disabled");
        assertThat(result.getPayload().map(Payload::getValue)).isEmpty();
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    public void get_default_variant_without_context() {
        // Set up a toggleName using UserWithIdStrategy
        ActivationStrategy strategy1 = new ActivationStrategy("unknown", null);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy1), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test", new Variant("Chuck", "Norris", true));

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Chuck");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("Norris");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void get_first_variant_with_context_provider() {

        UnleashContext context = UnleashContext.builder().userId("356").build();
        when(contextProvider.getContext()).thenReturn(context);

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 356");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("en");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("en");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void get_second_variant_with_context_provider() {

        UnleashContext context = UnleashContext.builder().userId("111").build();
        when(contextProvider.getContext()).thenReturn(context);

        // Set up a toggleName using UserWithIdStrategy
        Map<String, String> params = new HashMap<>();
        params.put("userIds", "123, 111, 121");
        ActivationStrategy strategy = new ActivationStrategy("userWithId", params);
        FeatureToggle featureToggle =
                new FeatureToggle("test", true, asList(strategy), getTestVariants());

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        final Variant result = unleash.getVariant("test");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("to");
        assertThat(result.getPayload().map(Payload::getValue).get()).isEqualTo("to");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    public void should_be_enabled_with_strategy_constraints() {
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(new Constraint("environment", Operator.IN, Arrays.asList("test")));
        ActivationStrategy activeStrategy = new ActivationStrategy("default", null, constraints);

        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(activeStrategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test")).isTrue();
    }

    @Test
    public void should_be_disabled_with_strategy_constraints() {
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(new Constraint("environment", Operator.IN, Arrays.asList("dev", "prod")));
        ActivationStrategy activeStrategy = new ActivationStrategy("default", null, constraints);

        FeatureToggle featureToggle = new FeatureToggle("test", true, asList(activeStrategy));

        when(toggleRepository.getToggle("test")).thenReturn(featureToggle);

        assertThat(unleash.isEnabled("test")).isFalse();
    }

    private List<VariantDefinition> getTestVariants() {
        return asList(
                new VariantDefinition(
                        "en", 50, new Payload("string", "en"), Collections.emptyList()),
                new VariantDefinition(
                        "to", 50, new Payload("string", "to"), Collections.emptyList()));
    }
}
