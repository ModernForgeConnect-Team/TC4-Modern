package github.com.gengyoubo.tcmodern.api.scan;

/** Register from common setup. This API artifact has no Minecraft or Forge dependencies. */
public final class TcScanApi {
    private static final TcScanRegistry REGISTRY = new TcScanRegistry();
    private TcScanApi() { }
    public static TcScanRegistry registry() { return REGISTRY; }
}
