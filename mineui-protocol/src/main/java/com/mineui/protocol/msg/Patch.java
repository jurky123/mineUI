package com.mineui.protocol.msg;

import java.util.List;

/**
 * 服务端 → 客户端：增量状态。信封 REVISION 字段即修订号；同一修订可包含多条操作。
 *
 * @param ops 操作列表（可为空，仅用于同步修订号）
 */
public record Patch(List<PatchOp> ops) {

    public Patch {
        ops = ops == null ? List.of() : List.copyOf(ops);
    }
}
