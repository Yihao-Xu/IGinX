package cn.edu.tsinghua.iginx.metadata.hook;

import java.util.Set;

public interface ActiveSeparatorSetChangeHook {

    void onChange(long iginxId, Set<String> separators);
}
