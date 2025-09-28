package io.github.ole.builtin;

public final class BuiltInContracts {
    private BuiltInContracts() {}

    public static final class VirtualDisplay {
        public static final String ID = "VirtualDisplay";
        public static final String ACTION_ATTACH = "attach";
        public static final String ARG_PARENT_SC = "parent";
        public static final String ARG_DISPLAY_ID = "display";
        public static final String ACTION_DETACH = "detach";
    }
}
