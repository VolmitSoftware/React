package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.SamplerHost;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Samplers count through mixins and attribute block and chunk counts to chunks. Samplers added after startup, such as
 * per-mod cost and API packs, get a map renderer as on Bukkit.
 */
final class ModSamplerHost implements SamplerHost {
    private final Supplier<SampleController> controller;
    private final ModMaps maps;
    private final ModChunkSamples chunks = new ModChunkSamples();

    ModSamplerHost(Supplier<SampleController> controller, ModMaps maps) {
        this.controller = controller;
        this.maps = maps;
    }

    @Override
    public Optional<SampleController> sampleController() {
        return Optional.ofNullable(controller.get());
    }

    @Override
    public void attachSampler(Sampler sampler) {
    }

    @Override
    public void detachSampler(Sampler sampler) {
    }

    @Override
    public void samplerRegistered(Sampler sampler) {
        maps.registerSampler(sampler);
    }

    @Override
    public void samplerUnregistered(String samplerId) {
        maps.unregisterRenderer(samplerId);
    }

    @Override
    public void refreshMonitors() {
    }

    ModChunkSamples chunks() {
        return chunks;
    }

    @Override
    public AtomicDouble chunkCounter(Object chunkOrBlock, Sampler sampler) {
        return chunks.counter(chunkOrBlock, sampler);
    }

    @Override
    public Optional<Double> sampleChunk(Object chunk, Sampler sampler) {
        return chunks.sample(chunk, sampler);
    }
}
