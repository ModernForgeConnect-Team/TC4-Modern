package github.com.gengyoubo.tcmodern.tc4.world;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
import net.minecraftforge.common.IPlantable;

/** Resumable MFR tree generation; all world access remains on the server thread. */
public final class MfrTickTree {
    private enum Kind { MEGA, SACRED, ENCHANTED_SACRED }
    private record LeafNode(int x, int y, int z, int branchY) { }
    private static final int[] OTHER = {2, 0, 0, 1, 2, 1};
    // Same dx-major, dz-minor order as the original leaf-layer loops.
    private static final int[] LEAF_OFFSETS_2 = leafOffsets(2), LEAF_OFFSETS_3 = leafOffsets(3);
    private final Level level;
    private final BlockPos origin;
    private final BlockState[] logs;
    private final BlockState leaves;
    private final boolean staticLeaves;
    private MassiveLeafSectionBatch leafSectionBatch;
    private final boolean nativeLeaves, nativeBark;
    private final IPlantable sapling;
    private static final class SavedRandom extends Random {
        private static final long MULTIPLIER=0x5DEECE66DL, MASK=(1L<<48)-1; private long state;
        SavedRandom(long seed) { super(seed); }
        @Override public synchronized void setSeed(long seed) { super.setSeed(seed); state=(seed^MULTIPLIER)&MASK; }
        @Override protected synchronized int next(int bits) { state=(state*MULTIPLIER+0xBL)&MASK; return (int)(state >>> (48-bits)); }
    }
    private final SavedRandom random;
    private final long seed;
    private final ArrayList<LeafNode> nodes = new ArrayList<>();
    // These cursors are used only by synchronous read-only scans; writes still receive immutable positions.
    private final BlockPos.MutableBlockPos readPos = new BlockPos.MutableBlockPos();
    private final int[] scanStart = new int[3], scanDelta = new int[3], scanPosition = new int[3];
    private final int[] lineStart = new int[3], lineDelta = new int[3], linePosition = new int[3];
    private boolean readCached;
    private int cachedX, cachedY, cachedZ;
    private BlockState cachedState;
    // Only used by the unsafe bedrock scan, while this synchronous generator owns the server thread.
    private long chunkMaxNanos, chunkTotalNanos; private int chunkCalls;
    private static final net.minecraft.server.level.TicketType<Long> TREE_TICKET=net.minecraft.server.level.TicketType.create("tc4modern_tree", Long::compare);
    private static final java.util.concurrent.atomic.AtomicLong NEXT_TICKET=new java.util.concurrent.atomic.AtomicLong();
    private final long ticketId=NEXT_TICKET.incrementAndGet();
    private net.minecraft.world.level.ChunkPos pendingChunk; private final java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos> heldTickets=new java.util.LinkedHashSet<>(); private int requests, waits, maxOutstanding, maxHeld; private boolean cooperative;
    // Diagnostic history is separate from held tickets: only actual addRegionTicket calls count.
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet requestedChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private long repeatRequests, ticketEvictions;
    private static final class AwaitChunk extends RuntimeException { private static final AwaitChunk INSTANCE=new AwaitChunk(); private AwaitChunk(){super(null,null,false,false);} }
    private void ensureChunk(int x,int z) {
        var server=(ServerLevel)level; var cache=server.getChunkSource(); int cx=x>>4,cz=z>>4;
        if(cache.getChunkNow(cx,cz)!=null) return;
        var target=new net.minecraft.world.level.ChunkPos(cx,cz);
        if(pendingChunk==null) {
            if(!heldTickets.contains(target)) {
                if(heldTickets.size()>=192) { var oldest=heldTickets.iterator().next(); cache.removeRegionTicket(TREE_TICKET,oldest,0,ticketId); heldTickets.remove(oldest); ticketEvictions++; }
                cache.addRegionTicket(TREE_TICKET,target,0,ticketId); heldTickets.add(target); requests++;
                if (!requestedChunks.add(target.toLong())) repeatRequests++;
                maxHeld=Math.max(maxHeld,heldTickets.size());
            }
            pendingChunk=target; maxOutstanding=1;
        }
        else if(!pendingChunk.equals(target)) throw new IllegalStateException("More than one pending chunk");
        waits++; throw AwaitChunk.INSTANCE;
    }
    private boolean pollChunk() {
        if(pendingChunk==null) return true;
        var cache=((ServerLevel)level).getChunkSource();
        if(cache.getChunkNow(pendingChunk.x,pendingChunk.z)==null) {waits++;return false;}
        pendingChunk=null; return true;
    }
    private void releaseChunk() {
        var cache=((ServerLevel)level).getChunkSource();
        for(var pos:heldTickets) cache.removeRegionTicket(TREE_TICKET,pos,0,ticketId);
        heldTickets.clear(); pendingChunk=null;
    }
    private void ensureLineChunks(int ax,int ay,int az,int bx,int by,int bz) {
        int[] a={ax,ay,az}, delta={bx-ax,by-ay,bz-az}, p=new int[3]; int major=0;
        for(int i=1;i<3;i++) if(Math.abs(delta[i])>Math.abs(delta[major])) major=i;
        if(delta[major]==0) return;
        int u=OTHER[major],v=OTHER[major+3],sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major],dv=(float)delta[v]/delta[major];
        for(int n=0;n!=delta[major]+sign;n+=sign) {
            p[major]=Mth.floor(a[major]+n+.5F);p[u]=Mth.floor(a[u]+n*du+.5F);p[v]=Mth.floor(a[v]+n*dv+.5F); ensureChunk(p[0],p[2]);
        }
    }
    private LevelChunk scanChunk;
    private int scanChunkX, scanChunkZ;
    private float heightAttenuation = .45F, branchSlope = .45F, scaleWidth = 4F, branchDensity = 3F;
    private int trunkRadius = 11, heightLimitLimit = 250, leafDistance = 4, minHeight = 80;
    private int heightLimit, height, leafBases, density;
    private boolean sloped, safe;

    private MfrTickTree(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves,
                           Kind kind, int scale, long seed, boolean staticLeaves) {
        this.level = level; this.origin = origin; this.sapling = sapling; this.logs = logs;
        this.staticLeaves = staticLeaves;
        this.leaves = staticLeaves ? leaves.setValue(LeavesBlock.PERSISTENT, true)
                .setValue(LeavesBlock.DISTANCE, 7).setValue(MagicalLeavesBlock.MASSIVE_STATIC, true) : leaves;
        this.nativeLeaves = leaves.getBlock() instanceof github.com.gengyoubo.tcmodern.tc4.content.MagicalLeavesBlock;
        this.nativeBark = logs[3].is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.GREATWOOD_WOOD.get())
                || logs[3].is(github.com.gengyoubo.tcmodern.tc4.init.Tc4Blocks.SILVERWOOD_WOOD.get());
        this.seed = seed; this.random = new SavedRandom(seed);
        if (kind != Kind.ENCHANTED_SACRED) {
            heightLimitLimit = scale * 12;
            minHeight = heightLimitLimit / 2;
            trunkRadius = (int)Math.round(scale / 2.0);
            leafDistance = minHeight > 30 ? 5 : minHeight / 8;
            if (kind == Kind.MEGA) {
                scaleWidth = .8F; branchDensity = .7F; heightAttenuation = .6F; sloped = true; safe = true;
            } else {
                scaleWidth = 1F; branchDensity = .9F; heightAttenuation = .35F; trunkRadius = Math.max(4, trunkRadius);
            }
        } else sloped = true;
    }

    public static final class Job implements TreeGenerationTask {
        private final MfrTickTree tree;
        private final boolean compactLeafBatches;
        private final boolean sectionLeafBatches;
        private java.util.concurrent.CompletableFuture<int[]> coordinates;
        private int cursor, branchCursor, phase, relativeY, y, candidateIndex, trunkColumn, leafNodeCursor, batchEnd; private boolean candidatePending; private int candidateX,candidateZ;
        private boolean batchCounted;
        private long batchedDuplicatePositions;
        public long maxStepNanos, totalStepNanos, prepareNanos, candidateNanos, nodeHash, trunkNanos;
        public final long[] phaseMaxNanos=new long[4]; public int steps, candidateCount, nodes;
        private long measuredSteps;
        public boolean done;
        private boolean cancelled;
        public long chunkMaxNanos() { return tree.chunkMaxNanos; }
        public long chunkTotalNanos() { return tree.chunkTotalNanos; }
        public int chunkCalls() { return tree.chunkCalls; }
        public String progress() {return "phase="+phase+" candidates="+candidateCount+" steps="+steps
                +" measuredSteps="+measuredSteps+" totalStepNanos="+totalStepNanos+" maxStepNanos="+maxStepNanos+" requests="+tree.requests
                +" waits="+tree.waits+" repeatRequests="+tree.repeatRequests+" uniqueRequestedChunks="+tree.requestedChunks.size()
                +" ticketEvictions="+tree.ticketEvictions+" heldTickets="+tree.heldTickets.size()+" pending="+tree.pendingChunk
                +" compactLeafBatches="+compactLeafBatches+" batchedDuplicatePositions="+batchedDuplicatePositions
                +" sectionLeafBatches="+sectionLeafBatches
                +" sectionFastWrites="+(tree.leafSectionBatch==null?0:tree.leafSectionBatch.fastWrites)
                +" sectionFallbackWrites="+(tree.leafSectionBatch==null?0:tree.leafSectionBatch.fallbackWrites)
                +" sectionMaxFlushNanos="+(tree.leafSectionBatch==null?0:tree.leafSectionBatch.maxFlushNanos);}
        public int requests() {return tree.requests;} public int waits(){return tree.waits;} public int maxOutstanding(){return tree.maxOutstanding;} public int maxHeld(){return tree.maxHeld;} public boolean ticketReleased(){return tree.pendingChunk==null && tree.heldTickets.isEmpty();} @Override public void cancel(){cancelled=true;if(coordinates!=null)coordinates.cancel(false);tree.releaseChunk();}
        public MfrMassiveTree.Result result(){return new MfrMassiveTree.Result(done,tree.heightLimit,tree.height,tree.trunkRadius,tree.sloped,tree.seed,tree.density,tree.nodes.size());}
        private Job(MfrTickTree tree, boolean compactLeafBatches, boolean sectionLeafBatches) {
            this.tree=tree; this.compactLeafBatches=compactLeafBatches; this.sectionLeafBatches=sectionLeafBatches;
            if(sectionLeafBatches) tree.leafSectionBatch=new MassiveLeafSectionBatch((ServerLevel)tree.level,tree.leaves);
            tree.cooperative=true; tree.setup(); int top=tree.origin.getY()+tree.heightLimit-tree.leafDistance;
            tree.nodes.add(new LeafNode(tree.origin.getX(),top,tree.origin.getZ(),tree.origin.getY()+tree.height));
            relativeY=top-tree.origin.getY(); y=top-1;
        }
        @Override public void save(net.minecraft.nbt.CompoundTag tag) {
            tag.putBoolean("StaticLeaves",tree.staticLeaves);
            tag.putBoolean("CompactLeafBatches",compactLeafBatches);
            tag.putBoolean("SectionLeafBatches",sectionLeafBatches);
            if(tree.leafSectionBatch!=null) {
                tag.putLong("SectionFastWrites",tree.leafSectionBatch.fastWrites);
                tag.putLong("SectionFallbackWrites",tree.leafSectionBatch.fallbackWrites);
                tag.putLong("SectionMaxFlushNanos",tree.leafSectionBatch.maxFlushNanos);
            }
            tag.putBoolean("LeafBatchCounted",batchCounted); tag.putLong("BatchedDuplicatePositions",batchedDuplicatePositions);
            tag.putLong("Seed",tree.seed); tag.putLong("RandomState",tree.random.state);
            tag.putInt("HeightLimit",tree.heightLimit); tag.putInt("Height",tree.height); tag.putInt("LeafBases",tree.leafBases); tag.putInt("Density",tree.density);
            tag.putInt("Phase",phase); tag.putInt("RelativeY",relativeY); tag.putInt("Y",y); tag.putInt("CandidateIndex",candidateIndex);
            tag.putBoolean("CandidatePending",candidatePending); tag.putInt("CandidateX",candidateX); tag.putInt("CandidateZ",candidateZ);
            tag.putInt("LeafNodeCursor",leafNodeCursor); tag.putInt("Cursor",cursor); tag.putInt("BranchCursor",branchCursor); tag.putInt("TrunkColumn",trunkColumn);
            tag.putInt("Steps",steps); tag.putInt("CandidateCount",candidateCount);
            var metrics = new net.minecraft.nbt.CompoundTag();
            metrics.putLong("MeasuredSteps",measuredSteps); metrics.putLong("TotalStepNanos",totalStepNanos); metrics.putLong("MaxStepNanos",maxStepNanos);
            metrics.putInt("Requests",tree.requests); metrics.putInt("Waits",tree.waits);
            metrics.putLong("RepeatRequests",tree.repeatRequests); metrics.putLong("TicketEvictions",tree.ticketEvictions);
            metrics.putLongArray("RequestedChunks",tree.requestedChunks.toLongArray());
            tag.put("Metrics",metrics);
            int[] saved=new int[tree.nodes.size()*4]; int i=0;
            for(var node:tree.nodes){saved[i++]=node.x;saved[i++]=node.y;saved[i++]=node.z;saved[i++]=node.branchY;}
            tag.putIntArray("Nodes",saved);
        }
        public int phase() { return phase; }
        public static Job restore(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves, net.minecraft.nbt.CompoundTag saved) {
            var nextTree=new MfrTickTree(level,origin,sapling,logs,leaves,Kind.ENCHANTED_SACRED,0,saved.getLong("Seed"),saved.getBoolean("StaticLeaves"));
            nextTree.heightLimit=saved.getInt("HeightLimit");nextTree.height=saved.getInt("Height");nextTree.leafBases=saved.getInt("LeafBases");nextTree.density=saved.getInt("Density");
            nextTree.random.state=saved.getLong("RandomState");
            var next=new Job(nextTree,saved.getBoolean("CompactLeafBatches"),saved.getBoolean("SectionLeafBatches")); next.tree.nodes.clear(); int[] data=saved.getIntArray("Nodes");
            if(next.tree.leafSectionBatch!=null) {
                next.tree.leafSectionBatch.fastWrites=saved.getLong("SectionFastWrites");
                next.tree.leafSectionBatch.fallbackWrites=saved.getLong("SectionFallbackWrites");
                next.tree.leafSectionBatch.maxFlushNanos=saved.getLong("SectionMaxFlushNanos");
            }
            if(data.length%4!=0) throw new IllegalStateException("Invalid saved node array");
            for(int i=0;i<data.length;i+=4)next.tree.nodes.add(new LeafNode(data[i],data[i+1],data[i+2],data[i+3]));
            next.phase=saved.getInt("Phase");next.relativeY=saved.getInt("RelativeY");next.y=saved.getInt("Y");next.candidateIndex=saved.getInt("CandidateIndex");
            next.candidatePending=saved.getBoolean("CandidatePending");next.candidateX=saved.getInt("CandidateX");next.candidateZ=saved.getInt("CandidateZ");
            next.leafNodeCursor=saved.getInt("LeafNodeCursor");next.branchCursor=saved.getInt("BranchCursor");next.trunkColumn=saved.getInt("TrunkColumn");
            next.steps=saved.getInt("Steps");next.candidateCount=saved.getInt("CandidateCount");
            if (saved.contains("Metrics",10)) {
                var metrics=saved.getCompound("Metrics");
                next.measuredSteps=metrics.getLong("MeasuredSteps"); next.totalStepNanos=metrics.getLong("TotalStepNanos"); next.maxStepNanos=metrics.getLong("MaxStepNanos");
                next.tree.requests=metrics.getInt("Requests"); next.tree.waits=metrics.getInt("Waits");
                next.tree.repeatRequests=metrics.getLong("RepeatRequests"); next.tree.ticketEvictions=metrics.getLong("TicketEvictions");
                for (long chunk : metrics.getLongArray("RequestedChunks")) next.tree.requestedChunks.add(chunk);
            }
            if(next.phase==1) {int savedCursor=saved.getInt("Cursor");next.startBatch();next.cursor=savedCursor;}
            next.batchCounted=saved.getBoolean("LeafBatchCounted"); next.batchedDuplicatePositions=saved.getLong("BatchedDuplicatePositions");
            return next;
        }
        private void candidate() {
            float layer=tree.layerSize(relativeY);
            if(layer>0) {
                if(!candidatePending) {
                    float distance=tree.scaleWidth*layer*(tree.random.nextFloat()+.328F);
                    float angle=tree.random.nextFloat()*2F*(float)Math.PI;
                    candidateX=Mth.floor(distance*Math.sin(angle)+tree.origin.getX()+.5F);
                    candidateZ=Mth.floor(distance*Math.cos(angle)+tree.origin.getZ()+.5F);
                    candidatePending=true; candidateCount++;
                }
                int x=candidateX,z=candidateZ;
                if(tree.checkLine(x,y,z,x,y+tree.leafDistance,z)==-1) {
                    int dx=tree.origin.getX()-x,dz=tree.origin.getZ()-z;
                    int drop=(int)(Math.sqrt(dx*dx+dz*dz)*tree.branchSlope);
                    int branchY=Math.min(y-drop,tree.origin.getY()+tree.height);
                    if(tree.checkLine(tree.origin.getX(),branchY,tree.origin.getZ(),x,y,z)==-1) tree.nodes.add(new LeafNode(x,y,z,branchY));
                }
                candidatePending=false;
                if(++candidateIndex<tree.density) return;
            }
            candidateIndex=0; relativeY--; y--;
        }
        private void startWorker() {
            startBatch();
        }
        private void startBatch() {
            int batchStart=leafNodeCursor;
            batchEnd=Math.min(batchStart+128,tree.nodes.size());
            var snapshot=tree.nodes.subList(batchStart,batchEnd).toArray(new LeafNode[0]); int leafDistance=tree.leafDistance;
            cursor=0; batchCounted=false;
            coordinates=java.util.concurrent.CompletableFuture.supplyAsync(()-> {
                long start=System.nanoTime(); int per=0;
                for(int layer=0;layer<leafDistance;layer++) per+=(layer!=0 && layer!=leafDistance-1?LEAF_OFFSETS_3:LEAF_OFFSETS_2).length/2;
                int[] coords=new int[Math.multiplyExact(Math.multiplyExact(snapshot.length,per),3)]; int k=0;
                var seen=compactLeafBatches ? new it.unimi.dsi.fastutil.longs.LongOpenHashSet(snapshot.length*per) : null;
                for(var node:snapshot) for(int layer=0;layer<leafDistance;layer++) {
                    int[] offsets=layer!=0 && layer!=leafDistance-1?LEAF_OFFSETS_3:LEAF_OFFSETS_2;
                    for(int i=0;i<offsets.length;i+=2) {
                        int x=node.x+offsets[i], y=node.y+layer, z=node.z+offsets[i+1];
                        if(seen!=null && !seen.add(BlockPos.asLong(x,y,z))) continue;
                        coords[k++]=x; coords[k++]=y; coords[k++]=z;
                    }
                }
                prepareNanos=System.nanoTime()-start; return k==coords.length ? coords : java.util.Arrays.copyOf(coords,k);
            });
        }
        private void trunkOne() {
            int r=tree.trunkRadius, side=2*r+1, dx=trunkColumn/side-r, dz=trunkColumn%side-r; trunkColumn++;
            if((dx*dx+dz*dz)*4>=r*r*5) return;
            int x0=tree.origin.getX(),y0=tree.origin.getY(),z0=tree.origin.getZ(),endY=y0+tree.height;
            double factor=400F/r;
            if(tree.sloped) endY=y0+sinc(factor*dx,factor*dz,tree.height)-(tree.random.nextInt(3)-1);
            int x=x0+dx,z=z0+dz;
            tree.placeLine(x,y0,z0+dz,x,endY,z,false); tree.put(new BlockPos(x,endY,z),tree.logs[3]);
            var ground=tree.readState(x,y0-1,z);
            if(ground.is(Blocks.GRASS_BLOCK)||ground.is(Blocks.MYCELIUM)||ground.is(Blocks.PODZOL)) tree.put(new BlockPos(x,y0-1,z),Blocks.DIRT.defaultBlockState());
        }
        @Override public boolean tick(long deadlineNanos) {
            if (done || cancelled) return true;
            tree.readCached=false; tree.scanChunk=null;
            int initialPhase=phase; long start=System.nanoTime(), deadline=deadlineNanos; steps++; measuredSteps++;
            try {
                if(!tree.pollChunk()) return false;
                if(phase==0) {
                    while(relativeY>=0 && System.nanoTime()<deadline) candidate();
                    if(relativeY<0) {startWorker(); phase=1;}
                }
                if(phase==1 && coordinates.isDone()) {
                    int[] c=coordinates.join();
                    if(!batchCounted) {
                        int per=0; for(int layer=0;layer<tree.leafDistance;layer++) per+=(layer!=0 && layer!=tree.leafDistance-1?LEAF_OFFSETS_3:LEAF_OFFSETS_2).length/2;
                        batchedDuplicatePositions+=(long)(batchEnd-leafNodeCursor)*per-c.length/3;
                        batchCounted=true;
                    }
                    while(cursor<c.length && System.nanoTime()<deadline) {
                        int x=c[cursor],y=c[cursor+1],z=c[cursor+2]; tree.ensureChunk(x,z); cursor+=3; var old=tree.readState(x,y,z);
                        if((tree.safe?tree.leafReplaceable(old):!old.is(Blocks.BEDROCK)) && !(tree.nativeLeaves && old==tree.leaves && !tree.level.captureBlockSnapshots)) tree.put(new BlockPos(x,y,z),tree.leaves);
                    }
                    if(cursor==c.length) {
                        leafNodeCursor=batchEnd;
                        if(leafNodeCursor==tree.nodes.size()) { coordinates=null; phase=2; }
                        else startBatch();
                    }
                }
                if(phase==2) {
                    int minimum=(int)(tree.heightLimit*.2F);
                    while(branchCursor<tree.nodes.size() && System.nanoTime()<deadline) {
                        var n=tree.nodes.get(branchCursor);
                        if(n.branchY-tree.origin.getY()>=minimum) tree.ensureLineChunks(tree.origin.getX(),n.branchY,tree.origin.getZ(),n.x,n.y,n.z);
                        branchCursor++;
                        if(n.branchY-tree.origin.getY()>=minimum) tree.placeLine(tree.origin.getX(),n.branchY,tree.origin.getZ(),n.x,n.y,n.z,true);
                    }
                    if(branchCursor==tree.nodes.size()) phase=3;
                }
                if(phase==3) {
                    long t=System.nanoTime(); int side=tree.trunkRadius*2+1;
                    while(trunkColumn<side*side && System.nanoTime()<deadline) {int dx=trunkColumn/side-tree.trunkRadius,dz=trunkColumn%side-tree.trunkRadius; tree.ensureChunk(tree.origin.getX()+dx,tree.origin.getZ()+dz); trunkOne();}
                    trunkNanos+=System.nanoTime()-t;
                    if(trunkColumn==side*side) {done=true;tree.releaseChunk();}
                }
            } catch(AwaitChunk wait) {
                // The exact candidate/placement cursor is retained until a later server tick.
            } finally {
                if(tree.leafSectionBatch!=null) tree.leafSectionBatch.flush();
                long elapsed=System.nanoTime()-start; totalStepNanos+=elapsed; maxStepNanos=Math.max(maxStepNanos,elapsed);
                phaseMaxNanos[initialPhase]=Math.max(phaseMaxNanos[initialPhase],elapsed); if(initialPhase==0) candidateNanos+=elapsed;
            }
            return done;
        }
    }
    public static Job begin(Level level, BlockPos origin, IPlantable sapling, BlockState[] logs, BlockState leaves, long seed) {
        var tree=new MfrTickTree(level,origin,sapling,logs,leaves,Kind.ENCHANTED_SACRED,0,seed,true);
        if(!tree.validLocation()) return null;
        return new Job(tree,true,true);
    }

    private void setup() {
        leafBases = Mth.ceil(heightLimit * heightAttenuation);
        density = candidateDensity(branchDensity, heightLimit);
    }
    public static int candidateDensity(float branchDensity, int heightLimit) {
        return Math.max(1, (int)(1.382D + Math.pow(branchDensity * heightLimit / 13.0D, 2.0D)));
    }
    private float layerSize(int y) {
        if (y < leafBases) return -1.618F;
        float half = heightLimit * .5F, delta = half - y;
        if (Math.abs(delta) >= half) return 0F;
        return .5F * (float)Math.sqrt(half * half - delta * delta);
    }
    private static int[] leafOffsets(int radius) {
        var offsets = new ArrayList<Integer>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx*dx + Math.abs(dx) + dz*dz + Math.abs(dz) + .5F > radius*radius) continue;
            offsets.add(dx); offsets.add(dz);
        }
        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }
    private static int sinc(double x, double z, int height) {
        double r = Math.hypot(x, z) / 180.0;
        if (r == 0) return height;
        double r2 = r * Math.PI / 1.5;
        return (int)Math.round(height * (Math.sin(r)/r + Math.sin(r2)/r2) / 2);
    }
    private boolean validLocation() {
        heightLimit = Math.min(heightLimitLimit + origin.getY(), level.getMaxBuildHeight() - 1) - origin.getY();
        if (heightLimit < minHeight || origin.getY() < level.getMinBuildHeight() + 1) return false;
        var below = origin.below();
        if (!level.getBlockState(below).canSustainPlant(level, below, Direction.UP, sapling)) return false;
        int clear = checkLine(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX(), origin.getY()+heightLimit-1, origin.getZ());
        if (clear != -1 && clear < minHeight) return false;
        if (clear != -1) heightLimit = clear;
        heightLimit = Math.min(heightLimit, heightLimitLimit);
        height = (int)(heightLimit * heightAttenuation);
        if (height >= heightLimit) height = heightLimit - 1;
        height += random.nextInt(heightLimit - height);
        if (safe) {
            double factor = 400F / trunkRadius;
            for (int dx = -trunkRadius; dx <= trunkRadius; dx++) for (int dz = -trunkRadius; dz <= trunkRadius; dz++) {
                if ((dx*dx + dz*dz)*4 >= trunkRadius*trunkRadius*5) continue;
                int top = origin.getY() + (sloped ? sinc(factor*dx, factor*dz, height) : height);
                if (checkLine(origin.getX()+dx, origin.getY(), origin.getZ()+dz,
                        origin.getX()+dx, top, origin.getZ()+dz) != -1) return false;
            }
        }
        return true;
    }
    private boolean leafReplaceable(BlockState state) {
        return state.isAir() || state.is(BlockTags.LEAVES) || state.canBeReplaced();
    }
    private boolean lineReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getBlock() == sapling
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof SaplingBlock;
    }
    /** Only the last read is cached; all writes invalidate it before any block callbacks can run. */
    private BlockState readState(int x, int y, int z) {
        if (readCached && x == cachedX && y == cachedY && z == cachedZ) return cachedState;
        if (cooperative && y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight()) ensureChunk(x, z);
        var state = level.getBlockState(readPos.set(x, y, z));
        cachedX = x; cachedY = y; cachedZ = z; cachedState = state; readCached = true;
        return state;
    }
    /** Matches MFR's dominant-axis line scan and floor rounding. */
    private int checkLine(int ax, int ay, int az, int bx, int by, int bz) {
        int[] a=scanStart, delta=scanDelta, p=scanPosition;
        a[0]=ax; a[1]=ay; a[2]=az;
        delta[0]=bx-ax; delta[1]=by-ay; delta[2]=bz-az;
        int major = 0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return -1;
        int u=OTHER[major], v=OTHER[major+3], sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major], dv=(float)delta[v]/delta[major];
        for (int n=0; n != delta[major]+sign; n+=sign) {
            p[major]=a[major]+n; p[u]=Mth.floor(a[u]+n*du); p[v]=Mth.floor(a[v]+n*dv);
            if (safe ? !lineReplaceable(readState(p[0],p[1],p[2])) : isBedrock(p[0],p[1],p[2]))
                return Math.abs(n);
        }
        return -1;
    }
    /** Empty sections cannot contain bedrock; avoid per-block lookups without caching their contents. */
    private boolean isBedrock(int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        // Subclasses may override getBlockState, so retain the normal query for them.
        if (level.getClass() != ServerLevel.class) return readState(x, y, z).is(Blocks.BEDROCK);
        int cx = x >> 4, cz = z >> 4;
        if (scanChunk == null || cx != scanChunkX || cz != scanChunkZ) {
            // Loading/populating a chunk can mutate another position previously cached.
            readCached = false;
            long chunkStarted=System.nanoTime(); if(cooperative) {ensureChunk(x,z); scanChunk=((ServerLevel)level).getChunkSource().getChunkNow(cx,cz);} else scanChunk=level.getChunk(cx,cz); long chunkElapsed=System.nanoTime()-chunkStarted; chunkMaxNanos=Math.max(chunkMaxNanos,chunkElapsed); chunkTotalNanos+=chunkElapsed; chunkCalls++;
            scanChunkX = cx; scanChunkZ = cz;
        }
        if (scanChunk.getSection(scanChunk.getSectionIndex(y)).hasOnlyAir()) return false;
        return readState(x, y, z).is(Blocks.BEDROCK);
    }
    /** Matches MFR's dominant-axis line placement, including its coordinate and axis rounding. */
    private void placeLine(int ax, int ay, int az, int bx, int by, int bz, boolean branches) {
        int[] a=lineStart, delta=lineDelta, p=linePosition;
        a[0]=ax; a[1]=ay; a[2]=az;
        delta[0]=bx-ax; delta[1]=by-ay; delta[2]=bz-az;
        int major=0; for (int i=1;i<3;i++) if (Math.abs(delta[i]) > Math.abs(delta[major])) major=i;
        if (delta[major] == 0) return;
        int u=OTHER[major],v=OTHER[major+3],sign=delta[major]>0?1:-1;
        float du=(float)delta[u]/delta[major],dv=(float)delta[v]/delta[major];
        for (int n=0;n!=delta[major]+sign;n+=sign) {
            p[major]=Mth.floor(a[major]+n+.5F); p[u]=Mth.floor(a[u]+n*du+.5F); p[v]=Mth.floor(a[v]+n*dv+.5F);
            int axis=3;
            if (!branches) {
                int dx=Math.abs(p[0]-a[0]), dz=Math.abs(p[2]-a[2]);
                axis=Math.max(dx,dz)==0?0:dx>=dz?1:2;
            }
            // Bark branches overlap heavily in the massive tree. A same-state LevelChunk write
            // returns without notifications; snapshots must still go through Level.setBlock.
            if (branches && nativeBark && !level.captureBlockSnapshots
                    && readState(p[0],p[1],p[2]) == logs[axis]) continue;
            put(new BlockPos(p[0],p[1],p[2]),logs[axis]);
        }
    }
    private void put(BlockPos pos, BlockState state) {
        // onPlace and neighbouring callbacks can change any block, so even a cached hit is invalid after a write.
        readCached = false;
        // The sapling is the persistent task anchor until all tree phases finish.
        if (!pos.equals(origin) && !level.isOutsideBuildHeight(pos)) {
            if (cooperative) ensureChunk(pos.getX(), pos.getZ());
            if (leafSectionBatch != null) {
                if (state == leaves && leafSectionBatch.tryPlace(pos)) return;
                leafSectionBatch.flush();
                if (state == leaves) leafSectionBatch.fallbackWrites++;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
