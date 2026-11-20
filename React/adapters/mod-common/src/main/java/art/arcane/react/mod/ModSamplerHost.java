package art.arcane.react.mod;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.core.controller.SampleController;
import art.arcane.react.platform.SamplerHost;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.Optional;
import java.util.function.Supplier;

/** Samplers count through mixins and attribute block and chunk counts to chunks; no monitors run on mod servers yet. */
final class ModSamplerHost implements SamplerHost {
    private final Supplier<SampleController> controller;
    private final ModChunkSamples chunks = new ModChunkSamples();

    ModSamplerHost(Supplier<SampleController> controller) {
        this.controller = controller;
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
    }

    @Override
    public void samplerUnregistered(String samplerId) {
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
