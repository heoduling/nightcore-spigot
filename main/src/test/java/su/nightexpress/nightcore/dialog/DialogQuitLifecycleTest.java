package su.nightexpress.nightcore.dialog;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.After;
import org.junit.Test;
import su.nightexpress.nightcore.NightCorePlugin;
import su.nightexpress.nightcore.menu.MenuListener;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.menu.api.Menu;
import su.nightexpress.nightcore.menu.click.ClickResult;
import su.nightexpress.nightcore.menu.impl.AbstractMenu;
import su.nightexpress.nightcore.menu.item.ItemHandler;
import su.nightexpress.nightcore.menu.item.ItemOptions;
import su.nightexpress.nightcore.menu.item.MenuItem;
import su.nightexpress.nightcore.menu.link.Linked;
import su.nightexpress.nightcore.menu.link.ViewLink;
import su.nightexpress.nightcore.util.bukkit.NightItem;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DialogQuitLifecycleTest {

    private final UUID id = UUID.randomUUID();
    private final AtomicInteger titles = new AtomicInteger();
    private final AtomicInteger uiCalls = new AtomicInteger();
    private final Player player = this.player();

    @After
    public void cleanup() {
        Dialog.shutdown();
        AbstractMenu.PLAYER_MENUS.clear();
    }

    @Test
    public void quitDiscardsDialogWithoutOpeningMenuOrSendingTitle() throws Exception {
        TestMenu menu = new TestMenu();
        this.dialog(this.player, menu);

        this.dialogQuit(this.player);

        assertNull(Dialog.get(this.player));
        assertEquals(0, menu.opens);
        assertEquals(0, this.titles.get());
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void timeoutQueuedBeforeQuitCannotRestoreUi() throws Exception {
        TestMenu menu = new TestMenu();
        this.dialog(this.player, menu).setTimeout(-1);
        List<Runnable> queued = new ArrayList<>();
        Dialog.checkTimeOut((owner, task) -> queued.add(task));
        assertEquals(1, queued.size());

        this.dialogQuit(this.player);
        queued.get(0).run();

        assertNull(Dialog.get(this.player));
        assertEquals(0, menu.opens);
        assertEquals(0, this.titles.get());
    }

    @Test
    public void normalStopStillReturnsToMenuAndClearsTitle() throws Exception {
        TestMenu menu = new TestMenu();
        this.dialog(this.player, menu);

        Dialog.stop(this.player);

        assertNull(Dialog.get(this.player));
        assertEquals(1, menu.opens);
        assertEquals(1, this.titles.get());
    }

    @Test
    public void oldQuitCannotDiscardNewLoginDialogWithSameUuid() throws Exception {
        Player current = this.player();
        Dialog replacement = this.dialog(current, null);

        this.dialogQuit(this.player);

        assertSame(replacement, Dialog.get(current));
        assertEquals(0, this.titles.get());
    }

    @Test
    public void oldStopCannotDiscardNewLoginDialogWithSameUuid() throws Exception {
        Player current = this.player();
        Dialog replacement = this.dialog(current, null);

        Dialog.stop(this.player);

        assertSame(replacement, Dialog.get(current));
        assertEquals(0, this.titles.get());
    }

    @Test
    public void menuQuitPurgesInternalViewerAndLinkWithoutUi() {
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(this.player);
        menu.getLink().set(viewer, new Object());
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);

        this.menuQuit(this.player);

        assertNull(AbstractMenu.getMenu(this.player));
        assertNull(menu.getViewer(this.player));
        assertNull(menu.getLink().get(this.player));
        assertEquals(0, menu.opens);
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void dialogQuitPurgesLastMenuEvenWhenGlobalEntryAlreadyGone() throws Exception {
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(this.player);
        menu.getLink().set(viewer, new Object());
        this.dialog(this.player, menu);
        assertNull(AbstractMenu.getMenu(this.player));

        this.dialogQuit(this.player);

        assertNull(menu.getViewer(this.player));
        assertNull(menu.getLink().get(this.player));
        assertEquals(0, menu.opens);
        assertEquals(0, this.titles.get());
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void oldMenuQuitPreservesCurrentLoginViewer() {
        Player current = this.player();
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(current);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);

        this.menuQuit(this.player);

        assertSame(menu, AbstractMenu.getMenu(current));
        assertSame(viewer, menu.getViewer(current));
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void oldOnClosePreservesCurrentLoginViewer() {
        Player current = this.player();
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(current);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);

        menu.onClose(this.player);

        assertSame(menu, AbstractMenu.getMenu(current));
        assertSame(viewer, menu.getViewer(current));
    }

    @Test
    public void closingLastMenuDoesNotRemoveDifferentActiveMenu() {
        TestMenu lastMenu = new TestMenu();
        lastMenu.getViewerOrCreate(this.player);
        TestMenu active = new TestMenu();
        MenuViewer current = active.getViewerOrCreate(this.player);
        AbstractMenu.PLAYER_MENUS.put(this.id, active);

        lastMenu.onClose(this.player);

        assertSame(active, AbstractMenu.getMenu(this.player));
        assertSame(current, active.getViewer(this.player));
        assertNull(lastMenu.getViewer(this.player));
    }

    @Test
    public void oldInventoryCloseEventCannotCloseNewLoginViewer() {
        Player current = this.player();
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(current);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);
        InventoryView oldView = proxy(InventoryView.class, (self, method, args) -> {
            if (method.getName().equals("getPlayer")) return this.player;
            throw new AssertionError("Unexpected inventory view call: " + method);
        });

        new MenuListener(null).onMenuClose(new InventoryCloseEvent(oldView));

        assertSame(menu, AbstractMenu.getMenu(current));
        assertSame(viewer, menu.getViewer(current));
    }

    @Test
    public void oldInventoryClickEventCannotInvokeNewLoginMenu() {
        Player current = this.player();
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(current);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);
        InventoryClickEvent event = new InventoryClickEvent(this.oldInventoryView(),
            InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        new MenuListener(null).onMenuItemClick(event);

        assertSame(menu, AbstractMenu.getMenu(current));
        assertSame(viewer, menu.getViewer(current));
        assertEquals(0, menu.clicks);
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void oldInventoryDragEventCannotInvokeNewLoginMenu() {
        Player current = this.player();
        TestMenu menu = new TestMenu();
        MenuViewer viewer = menu.getViewerOrCreate(current);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);
        InventoryDragEvent event = new InventoryDragEvent(this.oldInventoryView(), null, null, false, Map.of());

        new MenuListener(null).onMenuItemDrag(event);

        assertSame(menu, AbstractMenu.getMenu(current));
        assertSame(viewer, menu.getViewer(current));
        assertEquals(0, menu.drags);
        assertEquals(0, this.uiCalls.get());
    }

    @Test
    public void quitReleasesPersonalWeakItemAndItsCapturedPayload() throws Exception {
        TestMenu menu = new TestMenu();
        menu.getViewerOrCreate(this.player);
        WeakReference<Object> captured = this.addWeakItem(menu);
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);
        assertNotNull(captured.get());

        this.menuQuit(this.player);

        assertTrue(menu.getItems().isEmpty());
        for (int attempt = 0; attempt < 20 && captured.get() != null; attempt++) {
            System.gc();
            Thread.sleep(10);
        }
        assertNull("Removed weak item must release its callback payload", captured.get());
    }

    @Test
    public void opaqueMenuQuitKeepsOriginalMapOnlyCleanup() {
        MenuViewer viewer = new MenuViewer(this.player);
        Menu menu = proxy(Menu.class, (self, method, args) -> {
            if (method.getName().equals("getViewer")) return viewer;
            throw new AssertionError("Opaque menu must not receive UI cleanup calls: " + method);
        });
        AbstractMenu.PLAYER_MENUS.put(this.id, menu);

        this.menuQuit(this.player);

        assertNull(AbstractMenu.getMenu(this.player));
    }

    private WeakReference<Object> addWeakItem(TestMenu menu) {
        Object payload = new Object();
        MenuItem item = new MenuItem((NightItem) null);
        item.setOptions(ItemOptions.personalWeak(this.player));
        item.setHandler(ItemHandler.forClick((viewer, event) -> payload.toString()));
        menu.addItem(item);
        return new WeakReference<>(payload);
    }

    private Dialog dialog(Player owner, Menu menu) throws Exception {
        Dialog dialog = new Dialog(owner, (current, input) -> false).setLastMenu(menu);
        Field field = Dialog.class.getDeclaredField("DIALOG_MAP");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Dialog> dialogs = (Map<UUID, Dialog>) field.get(null);
        dialogs.put(this.id, dialog);
        return dialog;
    }

    private void dialogQuit(Player owner) {
        new DialogListener(null).onQuit(new PlayerQuitEvent(owner, ""));
    }

    private void menuQuit(Player owner) {
        new MenuListener(null).onQuit(new PlayerQuitEvent(owner, ""));
    }

    private Player player() {
        return proxy(Player.class, (self, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> this.id;
            case "sendTitle" -> { this.titles.incrementAndGet(); yield null; }
            case "getOpenInventory", "openInventory", "closeInventory" -> {
                this.uiCalls.incrementAndGet();
                throw new AssertionError("Quit cleanup must not access inventory UI");
            }
            default -> throw new AssertionError("Unexpected player call: " + method);
        });
    }

    private InventoryView oldInventoryView() {
        Inventory inventory = proxy(Inventory.class, (self, method, args) -> {
            if (method.getName().equals("getSize")) return 27;
            throw new AssertionError("Unexpected inventory call: " + method);
        });
        return proxy(InventoryView.class, (self, method, args) -> switch (method.getName()) {
            case "getPlayer" -> this.player;
            case "getTopInventory" -> inventory;
            case "convertSlot" -> args[0];
            case "getItem" -> null;
            default -> throw new AssertionError("Unexpected inventory view call: " + method);
        });
    }

    private static final class TestMenu extends AbstractMenu<NightCorePlugin> implements Linked<Object> {
        private final ViewLink<Object> link = new ViewLink<>();
        private int opens;
        private int clicks;
        private int drags;
        // Cleanup tests do not create inventories or initialize the server menu registry.
        private TestMenu() { super(null, new MenuOptions("Quit test", 27, null)); }
        public ViewLink<Object> getLink() { return this.link; }
        public boolean open(Player player) { this.opens++; return true; }
        public void onClick(MenuViewer viewer, ClickResult result, InventoryClickEvent event) { this.clicks++; }
        public void onDrag(MenuViewer viewer, InventoryDragEvent event) { this.drags++; }
        protected void onPrepare(MenuViewer viewer, MenuOptions options) {}
        protected void onReady(MenuViewer viewer, Inventory inventory) {}
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(self));
                    default -> throw new AssertionError(method);
                };
            }
            return handler.invoke(self, method, args);
        }));
    }
}
