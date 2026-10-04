package dev.schembuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Menu điều khiển Schem Builder (mở bằng Right Shift hoặc /sbuild menu). */
public class BuildScreen extends Screen {
    private static final int PER_PAGE = 8;
    private static int page;
    private static boolean showMaterials;
    private static String selected = "";
    private static String message = "";

    private final BuildEngine e = SchemBuilderClient.ENGINE;
    private List<String> files = new ArrayList<>();
    private List<String> mats = new ArrayList<>();
    private ButtonWidget startBtn, scaffBtn, moveBtn, speedLbl, reachLbl;
    private int px, py;
    private final int pw = 390, ph = 250;

    public BuildScreen() { super(Text.literal("Schem Builder")); }

    private Path dir() { return FabricLoader.getInstance().getGameDir().resolve("schematics"); }

    private void refreshFiles() {
        files.clear();
        try (Stream<Path> s = Files.list(dir())) {
            s.map(p -> p.getFileName().toString())
             .filter(n -> n.endsWith(".litematic") || n.endsWith(".schem"))
             .sorted(String.CASE_INSENSITIVE_ORDER).forEach(files::add);
        } catch (Exception ex) { message = "Không đọc được thư mục schematics"; }
        int maxPage = Math.max(0, (files.size() - 1) / PER_PAGE);
        if (page > maxPage) page = maxPage;
    }

    private void refreshMats() {
        mats = e.materials(MinecraftClient.getInstance());
    }

    private static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    @Override
    protected void init() {
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        refreshFiles();
        if (showMaterials) refreshMats();

        // ---- cột trái: danh sách file / vật liệu ----
        int y = py + 34;
        if (!showMaterials) {
            for (int i = 0; i < PER_PAGE; i++) {
                int idx = page * PER_PAGE + i;
                if (idx >= files.size()) break;
                String name = files.get(idx);
                addDrawableChild(ButtonWidget.builder(Text.literal((name.equals(selected) ? "> " : "") + cut(name, 26)), b -> loadFile(name))
                        .dimensions(px + 8, y, 180, 16).build());
                y += 18;
            }
        }
        int ny = py + 34 + PER_PAGE * 18 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { if (page > 0) { page--; clearAndInit(); } })
                .dimensions(px + 8, ny, 40, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { if ((page + 1) * PER_PAGE < files.size()) { page++; clearAndInit(); } })
                .dimensions(px + 52, ny, 40, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Làm mới"), b -> clearAndInit())
                .dimensions(px + 96, ny, 92, 14).build());

        // ---- cột phải: điều khiển ----
        int rx = px + 198, rw = 184, ry = py + 34;
        addDrawableChild(ButtonWidget.builder(Text.literal("Đặt gốc tại chỗ đứng"), b -> {
            var p = MinecraftClient.getInstance().player;
            if (p != null) { e.origin = p.getBlockPos(); message = "Gốc = " + e.origin.toShortString(); }
        }).dimensions(rx, ry, rw, 18).build());
        ry += 22;
        startBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Bắt đầu"), b -> toggleRun())
                .dimensions(rx, ry, rw, 18).build());
        ry += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("Dọn scaffold"), b -> {
            e.running = false; e.cleaning = true; message = "Đang dọn scaffold...";
        }).dimensions(rx, ry, rw, 18).build());
        ry += 22;
        scaffBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Scaffold"), b -> e.scaffoldEnabled = !e.scaffoldEnabled)
                .dimensions(rx, ry, rw, 18).build());
        ry += 22;
        moveBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Tự di chuyển"), b -> {
            e.autoMove = !e.autoMove;
            if (!e.autoMove) e.stopMoving(MinecraftClient.getInstance());
        }).dimensions(rx, ry, rw, 18).build());
        ry += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> e.blocksPerTick = Math.max(1, e.blocksPerTick - 1))
                .dimensions(rx, ry, 24, 18).build());
        speedLbl = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {}).dimensions(rx + 26, ry, rw - 52, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> e.blocksPerTick = Math.min(10, e.blocksPerTick + 1))
                .dimensions(rx + rw - 24, ry, 24, 18).build());
        ry += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> e.reach = Math.max(2.0, e.reach - 0.5))
                .dimensions(rx, ry, 24, 18).build());
        reachLbl = addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {}).dimensions(rx + 26, ry, rw - 52, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> e.reach = Math.min(6.0, e.reach + 0.5))
                .dimensions(rx + rw - 24, ry, 24, 18).build());
        ry += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(showMaterials ? "Hiện danh sách file" : "Hiện vật liệu cần"), b -> {
            showMaterials = !showMaterials; clearAndInit();
        }).dimensions(rx, ry, rw, 18).build());
    }

    private void loadFile(String name) {
        try {
            Schematic s = SchematicLoader.load(dir().resolve(name));
            e.load(s);
            selected = name;
            message = "Đã load " + name + ": " + s.blocks.size() + " block";
            clearAndInit();
        } catch (Exception ex) {
            message = "Lỗi: " + ex.getMessage();
        }
    }

    private void toggleRun() {
        if (e.running) {
            e.running = false;
            e.stopMoving(MinecraftClient.getInstance());
            message = "Đã dừng";
            return;
        }
        if (e.schematic == null) { message = "Chưa chọn file schematic"; return; }
        var p = MinecraftClient.getInstance().player;
        if (e.origin == null && p != null) e.origin = p.getBlockPos();
        e.start();
        message = "Đang build";
        close();
    }

    @Override
    public void tick() {
        if (showMaterials && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.age % 20 == 0) refreshMats();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        // cập nhật nhãn nút theo trạng thái hiện tại
        startBtn.setMessage(Text.literal(e.running ? "Dừng" : "Bắt đầu"));
        scaffBtn.setMessage(Text.literal("Scaffold: " + (e.scaffoldEnabled ? "BẬT" : "TẮT")));
        moveBtn.setMessage(Text.literal("Tự di chuyển: " + (e.autoMove ? "BẬT" : "TẮT")));
        speedLbl.setMessage(Text.literal("Tốc độ: " + e.blocksPerTick + " block/tick"));
        reachLbl.setMessage(Text.literal("Tầm với: " + e.reach));

        // khung nền
        c.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, 0xFF3FA9F5);
        c.fill(px, py, px + pw, py + ph, 0xF0141A22);

        super.render(c, mx, my, delta);

        c.drawTextWithShadow(textRenderer, Text.literal("Schem Builder v" + SchemBuilderClient.VERSION), px + 8, py + 8, 0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer, Text.literal(showMaterials ? "Vật liệu cần (cần / có)" : "Schematic (.litematic / .schem)"), px + 8, py + 22, 0xFF9AB4C8);

        if (showMaterials) {
            int y = py + 36;
            if (mats.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal("Chưa load schematic"), px + 8, y, 0xFFCCCCCC);
            for (int i = 0; i < Math.min(mats.size(), 12); i++) {
                c.drawTextWithShadow(textRenderer, Text.literal(cut(mats.get(i), 34)), px + 8, y, 0xFFE0E0E0);
                y += 12;
            }
        } else if (files.isEmpty()) {
            c.drawTextWithShadow(textRenderer, Text.literal("Bỏ file vào .minecraft/schematics"), px + 8, py + 38, 0xFFCCCCCC);
        }

        // thanh tiến độ + trạng thái
        int total = e.total();
        double ratio = total > 0 ? Math.min(1.0, (double) e.cursorValue() / total) : 0;
        int by = py + ph - 62;
        c.fill(px + 8, by, px + pw - 8, by + 8, 0xFF2A323D);
        c.fill(px + 8, by, px + 8 + (int) ((pw - 16) * ratio), by + 8, 0xFF4CD964);
        c.drawTextWithShadow(textRenderer, Text.literal(String.format("%.1f%%  |  đã đặt %d  |  bỏ qua %d  |  scaffold %d  |  %d block",
                ratio * 100, e.placed(), e.skipped(), e.scaffoldCount(), total)), px + 8, by + 12, 0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer, Text.literal("Gốc: " + (e.origin == null ? "chưa đặt" : e.origin.toShortString())
                + "  |  " + (e.running ? "ĐANG CHẠY" : e.cleaning ? "ĐANG DỌN" : "dừng")), px + 8, by + 24, 0xFFFFD866);
        c.drawTextWithShadow(textRenderer, Text.literal(cut(e.lastInfo, 62)), px + 8, by + 36, 0xFF9AB4C8);
        if (!message.isEmpty()) c.drawTextWithShadow(textRenderer, Text.literal(cut(message, 62)), px + 8, by + 48, 0xFFFFA070);
    }
}
