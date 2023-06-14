package cn.edu.tsinghua.iginx.metadata.statistics;

public final class IginxStatistics {

    // iginx 的热度
    private double heat;

    public IginxStatistics(double heat) {
        this.heat = heat;
    }

    public double getHeat() {
        return heat;
    }

    public void setHeat(double heat) {
        this.heat = heat;
    }
}
