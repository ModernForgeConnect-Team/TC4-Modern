package github.com.gengyoubo.tcmodern.tc4.mfclpatch.mixin;

import github.com.gengyoubo.tcmodern.tc4.mfclpatch.MfclPatchRuntime;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boots the old runtime with the backported legacy helper classes instead of MFCL 0.2.10's embedded helper JAR. */
@Mixin(targets = "github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime", remap = false)
abstract class EmbeddedLegacyRuntimeMixin {
    @Inject(method = "extractHelper(Ljava/nio/file/Path;)Ljava/nio/file/Path;", at = @At("HEAD"), cancellable = true)
    private static void tc4modern$patchedHelper(Path gameDirectory, CallbackInfoReturnable<Path> result) throws java.io.IOException {
        result.setReturnValue(MfclPatchRuntime.legacyHelper(gameDirectory));
    }
}
