package com.android.server.security.trustcircle.tlv.command.register;

import com.android.server.security.trustcircle.tlv.core.TLVTree;

public class RET_UNREG_REQ extends TLVTree.TLVRootTree {
    public static final int ID = -2147483633;

    public int getCmdID() {
        return ID;
    }

    public short getTreeTag() {
        return 0;
    }
}
