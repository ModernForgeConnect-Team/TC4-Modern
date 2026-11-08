package github.com.gengyoubo.tcmodern.tc4.world;

import net.minecraft.nbt.CompoundTag;

/** A server-thread tree job that cooperatively yields when its time budget or a chunk is unavailable. */
public interface TreeGenerationTask {
    /** Runs work until the monotonic deadline; returns true when generation has finished. */
    boolean tick(long deadlineNanos);

    /** Releases resources held by this job. */
    void cancel();

    /** Saves the progress needed to resume the job. */
    void save(CompoundTag tag);
}
