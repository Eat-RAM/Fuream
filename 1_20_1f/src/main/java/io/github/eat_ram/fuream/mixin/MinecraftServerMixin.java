package io.github.eat_ram.fuream.mixin;

import java.io.FileNotFoundException;
import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonParseException;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.FureamWorldConfigImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

@Mixin(net.minecraft.server.MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow
    public abstract Path getSavePath(WorldSavePath worldSavePath);

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void createWorldConfig(CallbackInfo info) {
        final MinecraftServer THIS = (MinecraftServer)(Object)this;
        final FureamWorldConfigImpl CONFIG = new FureamWorldConfigImpl();
        FureamMain.WORLD_CONFIGS.put(THIS, CONFIG);
        try {
            FureamWorldConfigImpl.readWorldConfig(CONFIG, THIS);
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (FileNotFoundException ignored) {}
    }
}
