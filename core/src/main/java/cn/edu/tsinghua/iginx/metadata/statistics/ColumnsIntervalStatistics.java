package cn.edu.tsinghua.iginx.metadata.statistics;

public class ColumnsIntervalStatistics {

  // 序列区间的热度
  private double heat;

  public ColumnsIntervalStatistics() {}

  public ColumnsIntervalStatistics(double heat) {
    this.heat = heat;
  }

  public void update(ColumnsIntervalStatistics statistics) {
    heat += statistics.getHeat();
  }

  public double getHeat() {
    return heat;
  }

  public void setHeat(double heat) {
    this.heat = heat;
  }
}
