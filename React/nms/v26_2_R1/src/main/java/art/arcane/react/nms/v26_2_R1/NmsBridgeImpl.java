package art.arcane.react.nms.v26_2_R1;

import art.arcane.react.nms.BrewingTickHook;
import art.arcane.react.nms.ExplosionHook;
import art.arcane.react.nms.ExplosionPacketSuppressor;
import art.arcane.react.nms.FallingBlockTickHook;
import art.arcane.react.nms.FurnaceTickHook;
import art.arcane.react.nms.HopperTickHook;
import art.arcane.react.nms.NmsBridge;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NmsBridgeImpl implements NmsBridge {
    private static final Logger LOG = Logger.getLogger("React-NMS-v26_2_R1");

    private final Set<String> patchedTargets = new HashSet<>();
    private volatile boolean furnaceInstalled;
    private volatile boolean brewingInstalled;
    private volatile boolean fallingInstalled;
    private volatile boolean explosionInstalled;
    private volatile boolean explosionPacketSubstituted;
    private volatile boolean hopperInstalled;

    @Override
    public String version() {
        return "v26_2_R1";
    }

    @Override
    public boolean installFurnaceTickHook(FurnaceTickHook hook) {
        if (hook == null) {
            configureHook("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity", FurnaceTickRuntime.class, null);
            return true;
        }
        if (!ensureFurnacePatched()) {
            return false;
        }
        return configureHook("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity", FurnaceTickRuntime.class, hook);
    }

    @Override
    public boolean installBrewingTickHook(BrewingTickHook hook) {
        if (hook == null) {
            configureHook("net.minecraft.world.level.block.entity.BrewingStandBlockEntity", BrewingTickRuntime.class, null);
            return true;
        }
        if (!ensureBrewingPatched()) {
            return false;
        }
        return configureHook("net.minecraft.world.level.block.entity.BrewingStandBlockEntity", BrewingTickRuntime.class, hook);
    }

    @Override
    public boolean installFallingBlockTickHook(FallingBlockTickHook hook) {
        if (hook == null) {
            configureHook("net.minecraft.world.entity.item.FallingBlockEntity", FallingBlockTickRuntime.class, null);
            return true;
        }
        if (!ensureFallingPatched()) {
            return false;
        }
        return configureHook("net.minecraft.world.entity.item.FallingBlockEntity", FallingBlockTickRuntime.class, hook);
    }

    @Override
    public boolean installExplosionHook(ExplosionHook hook) {
        if (hook == null) {
            configureHook("net.minecraft.world.level.ServerExplosion", ExplosionRuntime.class, null);
            return true;
        }
        if (!ensureExplosionPatched()) {
            return false;
        }
        return configureHook("net.minecraft.world.level.ServerExplosion", ExplosionRuntime.class, hook);
    }

    @Override
    public boolean installExplosionPacketSuppressor(ExplosionPacketSuppressor suppressor) {
        if (suppressor == null) {
            configureHook("net.minecraft.server.level.ServerLevel", ExplosionPacketSubstitution.class, null);
            return true;
        }
        if (!ensureExplodePacketSubstituted()) {
            return false;
        }
        return configureHook("net.minecraft.server.level.ServerLevel", ExplosionPacketSubstitution.class, suppressor);
    }

    @Override
    public boolean installHopperTickHook(HopperTickHook hook) {
        if (hook == null) {
            configureHook("net.minecraft.world.level.block.entity.HopperBlockEntity", HopperTickRuntime.class, null);
            return true;
        }
        if (!ensureHopperPatched()) {
            return false;
        }
        return configureHook("net.minecraft.world.level.block.entity.HopperBlockEntity", HopperTickRuntime.class, hook);
    }

    @Override
    public void uninstallFurnaceTickHook() {
        configureHook("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity", FurnaceTickRuntime.class, null);
    }

    @Override
    public void uninstallBrewingTickHook() {
        configureHook("net.minecraft.world.level.block.entity.BrewingStandBlockEntity", BrewingTickRuntime.class, null);
    }

    @Override
    public void uninstallFallingBlockTickHook() {
        configureHook("net.minecraft.world.entity.item.FallingBlockEntity", FallingBlockTickRuntime.class, null);
    }

    @Override
    public void uninstallExplosionHook() {
        configureHook("net.minecraft.world.level.ServerExplosion", ExplosionRuntime.class, null);
    }

    @Override
    public void uninstallExplosionPacketSuppressor() {
        configureHook("net.minecraft.server.level.ServerLevel", ExplosionPacketSubstitution.class, null);
    }

    @Override
    public void uninstallHopperTickHook() {
        configureHook("net.minecraft.world.level.block.entity.HopperBlockEntity", HopperTickRuntime.class, null);
    }

    @Override
    public boolean supportsFurnaceTickHook() {
        return furnaceInstalled || classExists("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity");
    }

    @Override
    public boolean supportsBrewingTickHook() {
        return brewingInstalled || classExists("net.minecraft.world.level.block.entity.BrewingStandBlockEntity");
    }

    @Override
    public boolean supportsFallingBlockTickHook() {
        return fallingInstalled || classExists("net.minecraft.world.entity.item.FallingBlockEntity");
    }

    @Override
    public boolean supportsExplosionHook() {
        return explosionInstalled || classExists("net.minecraft.world.level.ServerExplosion");
    }

    @Override
    public boolean supportsExplosionPacketSuppressor() {
        return explosionPacketSubstituted
                || (classExists("net.minecraft.server.level.ServerLevel")
                        && classExists("net.minecraft.server.network.ServerGamePacketListenerImpl")
                        && classExists("net.minecraft.network.protocol.game.ClientboundExplodePacket"));
    }

    @Override
    public boolean supportsHopperTickHook() {
        return hopperInstalled || classExists("net.minecraft.world.level.block.entity.HopperBlockEntity");
    }

    @Override
    public boolean broadcastMergedExplosion(World world, double x, double y, double z, float radius, double rangeBlocks) {
        if (world == null) {
            return false;
        }
        try {
            ServerLevel serverLevel = ((CraftWorld) world).getHandle();
            MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();
            PlayerList playerList = minecraftServer.getPlayerList();
            Vec3 center = new Vec3(x, y, z);
            ParticleOptions explosionParticle = radius >= 2.0F ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION;
            Holder<SoundEvent> explosionSound = SoundEvents.GENERIC_EXPLODE;
            WeightedList<ExplosionParticleInfo> blockParticles = WeightedList.of();
            Packet<?> packet = new ClientboundExplodePacket(center, radius, 0, Optional.<Vec3>empty(), explosionParticle, explosionSound, blockParticles);
            playerList.broadcast(null, x, y, z, rangeBlocks, serverLevel.dimension(), packet);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[React] broadcastMergedExplosion failed: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " - " + t.getMessage()), t);
            return false;
        }
    }

    private synchronized boolean ensureFurnacePatched() {
        if (furnaceInstalled) {
            return true;
        }
        boolean ok = installAdvice(
                "net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity",
                "serverTick",
                FurnaceTickAdvice.class,
                FurnaceTickRuntime.class,
                4);
        if (ok) {
            furnaceInstalled = true;
        }
        return ok;
    }

    private synchronized boolean ensureBrewingPatched() {
        if (brewingInstalled) {
            return true;
        }
        boolean ok = installAdvice(
                "net.minecraft.world.level.block.entity.BrewingStandBlockEntity",
                "serverTick",
                BrewingTickAdvice.class,
                BrewingTickRuntime.class,
                4);
        if (ok) {
            brewingInstalled = true;
        }
        return ok;
    }

    private synchronized boolean ensureFallingPatched() {
        if (fallingInstalled) {
            return true;
        }
        boolean ok = installAdvice(
                "net.minecraft.world.entity.item.FallingBlockEntity",
                "tick",
                FallingBlockTickAdvice.class,
                FallingBlockTickRuntime.class,
                0);
        if (ok) {
            fallingInstalled = true;
        }
        return ok;
    }

    private synchronized boolean ensureExplosionPatched() {
        if (explosionInstalled) {
            return true;
        }
        boolean ok = installAdvice(
                "net.minecraft.world.level.ServerExplosion",
                "explode",
                ExplosionAdvice.class,
                ExplosionRuntime.class,
                -1);
        if (ok) {
            explosionInstalled = true;
        }
        return ok;
    }

    private synchronized boolean ensureExplodePacketSubstituted() {
        if (explosionPacketSubstituted) {
            return true;
        }
        String key = "net.minecraft.server.level.ServerLevel#explode0->ExplosionPacketSubstitution";
        if (patchedTargets.contains(key)) {
            explosionPacketSubstituted = true;
            return true;
        }
        Instrumentation instrumentation = attachAgent();
        if (instrumentation == null) {
            LOG.log(Level.WARNING, "[React] NMS substitution install failed: bytecode agent not attached for " + key);
            return false;
        }
        try {
            Class<?> targetClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> substitutionClass = ensureVisibleToTarget(ExplosionPacketSubstitution.class, targetClass.getClassLoader());
            Method replacement = substitutionClass.getDeclaredMethod(
                    "maybeSend",
                    ServerGamePacketListenerImpl.class,
                    Packet.class);
            ElementMatcher.Junction<MethodDescription> targetMethod = ElementMatchers.named("explode0");
            ElementMatcher.Junction<MethodDescription> sendMatcher =
                    ElementMatchers.<MethodDescription>named("send")
                            .and(ElementMatchers.takesArguments(Packet.class));
            new ByteBuddy()
                    .redefine(targetClass)
                    .visit(Advice.to(ExplosionPacketScopeAdvice.class).on(targetMethod))
                    .visit(MemberSubstitution.relaxed()
                            .method(sendMatcher)
                            .replaceWith(replacement)
                            .on(targetMethod))
                    .make()
                    .load(targetClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            patchedTargets.add(key);
            explosionPacketSubstituted = true;
            return true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[React] NMS substitution install failed for " + key + ": "
                    + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " - " + t.getMessage()), t);
            return false;
        }
    }

    private synchronized boolean ensureHopperPatched() {
        if (hopperInstalled) {
            return true;
        }
        boolean ok = false;
        String[] methodCandidates = new String[] {"pushItemsTick", "serverTick", "tryMoveItems"};
        for (String name : methodCandidates) {
            if (installAdvice(
                    "net.minecraft.world.level.block.entity.HopperBlockEntity",
                    name,
                    HopperTickAdvice.class,
                    HopperTickRuntime.class,
                    4)) {
                ok = true;
                break;
            }
        }
        if (ok) {
            hopperInstalled = true;
        }
        return ok;
    }

    private boolean installAdvice(String targetClassName, String methodName, Class<?> adviceClass, Class<?> runtimeClass, int requiredArgCount) {
        String adviceClassName = adviceClass.getName();
        String key = targetClassName + "#" + methodName + "->" + adviceClassName;
        if (patchedTargets.contains(key)) {
            return true;
        }
        Instrumentation instrumentation = attachAgent();
        if (instrumentation == null) {
            LOG.log(Level.WARNING, "[React] NMS advice install failed: bytecode agent not attached for " + key);
            return false;
        }
        try {
            Class<?> targetClass = Class.forName(targetClassName);
            ensureVisibleToTarget(runtimeClass, targetClass.getClassLoader());
            ElementMatcher.Junction<MethodDescription> matcher = ElementMatchers.named(methodName);
            if (requiredArgCount >= 0) {
                matcher = matcher.and(ElementMatchers.takesArguments(requiredArgCount));
            }
            boolean methodExists = false;
            for (Method method : targetClass.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (requiredArgCount >= 0 && method.getParameterCount() != requiredArgCount) {
                    continue;
                }
                methodExists = true;
                break;
            }
            if (!methodExists) {
                LOG.log(Level.WARNING, "[React] NMS advice install skipped: " + targetClassName + "#" + methodName
                        + " (arity=" + requiredArgCount + ") not found");
                return false;
            }
            new ByteBuddy()
                    .redefine(targetClass)
                    .visit(Advice.to(adviceClass).on(matcher))
                    .make()
                    .load(targetClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            patchedTargets.add(key);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[React] NMS advice install failed for " + key + ": " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " - " + t.getMessage()), t);
            return false;
        }
    }

    private boolean configureHook(String targetClassName, Class<?> hookOwnerClass, Object hook) {
        try {
            Class<?> targetClass = Class.forName(targetClassName);
            Class<?> visibleHookOwnerClass = ensureVisibleToTarget(hookOwnerClass, targetClass.getClassLoader());
            Method configure = visibleHookOwnerClass.getDeclaredMethod("configure", Object.class);
            configure.setAccessible(true);
            configure.invoke(null, hook);
            return true;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "[React] NMS hook configure failed for " + targetClassName + " using "
                    + hookOwnerClass.getName() + ": " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " - " + t.getMessage()), t);
            return false;
        }
    }

    private Class<?> ensureVisibleToTarget(Class<?> sourceClass, ClassLoader targetClassLoader) throws Exception {
        if (sourceClass.getClassLoader() == targetClassLoader) {
            return sourceClass;
        }
        try {
            return Class.forName(sourceClass.getName(), false, targetClassLoader);
        } catch (ClassNotFoundException ignored) {
        }

        Map<String, byte[]> bytesByName = ClassFileLocator.ForClassLoader.readToNames(sourceClass);
        Map<String, Class<?>> injected;
        if (targetClassLoader == null) {
            injected = ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(bytesByName);
        } else if (ClassInjector.UsingUnsafe.isAvailable()) {
            injected = new ClassInjector.UsingUnsafe(targetClassLoader).injectRaw(bytesByName);
        } else {
            injected = new ClassInjector.UsingReflection(targetClassLoader).injectRaw(bytesByName);
        }
        Class<?> injectedClass = injected.get(sourceClass.getName());
        if (injectedClass == null) {
            throw new ClassNotFoundException(sourceClass.getName());
        }
        return injectedClass;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static volatile Instrumentation cachedInstrumentation;

    private static Instrumentation attachAgent() {
        Instrumentation existing = cachedInstrumentation;
        if (existing != null) {
            return existing;
        }
        synchronized (NmsBridgeImpl.class) {
            if (cachedInstrumentation != null) {
                return cachedInstrumentation;
            }
            try {
                String shadedPrefix = ByteBuddyAgent.class.getName().replace(".agent.ByteBuddyAgent", "");
                System.setProperty(shadedPrefix + ".experimental", "true");
                cachedInstrumentation = ByteBuddyAgent.install();
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "[React] ByteBuddyAgent install failed: " + t.getMessage(), t);
            }
            return cachedInstrumentation;
        }
    }
}
