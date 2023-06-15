package cn.edu.tsinghua.iginx.metadata.statistics;

public final class StorageEngineStatistics {

  // 存储引擎的热度
  private double heat;

  // 存储引擎的能力
  private double capacity;

  // 存储引擎的饱和度
  private double saturation;

  public StorageEngineStatistics() {}

  public StorageEngineStatistics(double heat, double capacity, double saturation) {
    this.heat = heat;
    this.capacity = capacity;
    this.saturation = saturation;
  }

  public void updateByStorageEngineStatistics(StorageEngineStatistics statistics) {
    heat += statistics.getHeat();
    //        capacity = statistics.getCapacity();
    //        saturation += statistics.getSaturation();
  }

  public void updateByTimeSeriesStatistics(ColumnStatistics statistics) {
    heat += statistics.getTotalHeat();
    //        saturation += statistics.getWriteBytes();
  }

  public double getHeat() {
    return heat;
  }

  public void setHeat(double heat) {
    this.heat = heat;
  }

  public double getCapacity() {
    return capacity;
  }

  public void setCapacity(double capacity) {
    this.capacity = capacity;
  }

  public double getSaturation() {
    return saturation;
  }

  public void setSaturation(double saturation) {
    this.saturation = saturation;
  }
}
