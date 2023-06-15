package cn.edu.tsinghua.iginx.metadata.hook;

import cn.edu.tsinghua.iginx.metadata.statistics.StorageEngineStatistics;
import java.util.Map;

public interface ActiveStorageEngineStatisticsChangeHook {

    void onChange(long id, Map<Long, StorageEngineStatistics> statisticsMap);
}
