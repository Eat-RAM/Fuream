package io.github.eat_ram.fuream.compat;

/** Loads one of the four internal adapters without linking newer Bukkit API types. */
public final class VersionAdapters {
    private static final VersionAdapter CURRENT = select(ServerVersion.CURRENT);

    public static VersionAdapter current() {
        return CURRENT;
    }

    static VersionAdapter select(ServerVersion version) {
        if (version.atMost(1, 12)) return new LegacyAdapter();
        if (!version.atLeast(1, 17)) return new FlatteningObfuscatedAdapter();
        if (!version.atLeast(1, 20, 5)) return new MojangNamedAdapter();
        return new ComponentsAdapter();
    }

    private abstract static class BaseAdapter implements VersionAdapter {
        private final String id;
        private final boolean specialFurnaces;
        private final boolean recipeOverrides;
        private final boolean dataComponents;

        private BaseAdapter(
            String id, boolean specialFurnaces, boolean recipeOverrides, boolean dataComponents
        ) {
            this.id = id;
            this.specialFurnaces = specialFurnaces;
            this.recipeOverrides = recipeOverrides;
            this.dataComponents = dataComponents;
        }

        @Override
        public final String id() {
            return this.id;
        }

        @Override
        public final boolean supportsSpecialFurnaces() {
            return this.specialFurnaces;
        }

        @Override
        public final boolean supportsRecipeOverrides() {
            return this.recipeOverrides;
        }

        @Override
        public final boolean usesDataComponents() {
            return this.dataComponents;
        }
    }

    private static final class LegacyAdapter extends BaseAdapter {
        private LegacyAdapter() {
            super("legacy-numeric", false, false, false);
        }
    }

    private static final class FlatteningObfuscatedAdapter extends BaseAdapter {
        private FlatteningObfuscatedAdapter() {
            super("flattening-obfuscated", true, true, false);
        }
    }

    private static final class MojangNamedAdapter extends BaseAdapter {
        private MojangNamedAdapter() {
            super("mojang-named", true, true, false);
        }
    }

    private static final class ComponentsAdapter extends BaseAdapter {
        private ComponentsAdapter() {
            super("components", true, true, true);
        }
    }

    private VersionAdapters() {
        throw new UnsupportedOperationException();
    }
}
