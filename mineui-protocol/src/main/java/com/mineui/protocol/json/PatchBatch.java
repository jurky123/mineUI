package com.mineui.protocol.json;

import com.mineui.protocol.msg.PatchOp;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个 batch / 一次动作内的 PATCH 操作累积器。
 * <p>
 * 按写入顺序追加，不做跨路径去重：父子路径混合写入（如先 {@code player.name}
 * 后整体覆盖 {@code player}）时，去重会留下已失效的子路径操作导致客户端无法应用；
 * 按序保留则客户端按序应用后必然与服务端最终状态一致。仍合并为一个 PATCH 下发，
 * 包数量不变，只是包内操作数略增。
 */
public final class PatchBatch {

    private final List<PatchOp> ops = new ArrayList<>();

    /** 记录一次写入产生的同步操作（value 应为快照，见 {@link JsonPointers#syncOp}）。 */
    public void add(PatchOp op) {
        if (op != null) {
            ops.add(op);
        }
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    /** 取出按序累积的操作并清空（返回拷贝，调用方可直接下发）。 */
    public List<PatchOp> drain() {
        List<PatchOp> drained = List.copyOf(ops);
        ops.clear();
        return drained;
    }

    /** 丢弃全部待发操作（如会话已关闭）。 */
    public void clear() {
        ops.clear();
    }
}
