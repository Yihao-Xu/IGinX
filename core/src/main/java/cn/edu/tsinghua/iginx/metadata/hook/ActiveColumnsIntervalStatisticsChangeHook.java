package cn.edu.tsinghua.iginx.metadata.hook;

import cn.edu.tsinghua.iginx.metadata.entity.ColumnsInterval;
import cn.edu.tsinghua.iginx.metadata.statistics.ColumnsIntervalStatistics;
import java.util.Map;

public interface ActiveColumnsIntervalStatisticsChangeHook {

    void onChange(Map<ColumnsInterval, ColumnsIntervalStatistics> statisticsMap);
}
