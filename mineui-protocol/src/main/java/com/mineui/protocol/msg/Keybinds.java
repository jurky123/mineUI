package com.mineui.protocol.msg;

import java.util.List;

/** 键位声明集合（服务端全量下发，客户端整体替换）。 */
public record Keybinds(List<Keybind> keybinds) {

    public Keybinds {
        keybinds = keybinds == null ? List.of() : List.copyOf(keybinds);
    }

    public static Keybinds empty() {
        return new Keybinds(List.of());
    }
}
