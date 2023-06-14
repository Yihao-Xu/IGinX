package cn.edu.tsinghua.iginx.metadata.statistics;

public class ColumnStatistics {

    // 序列的写热度
    private double writeHeat;

    // 序列的读热度
    private double readHeat;

    // 序列的总热度
    private double totalHeat;

    private long storageEngineId;

    public ColumnStatistics() {}

    public ColumnStatistics(
            double writeHeat, double readHeat, double totalHeat, long storageEngineId) {
        this.writeHeat = writeHeat;
        this.readHeat = readHeat;
        this.totalHeat = totalHeat;
        this.storageEngineId = storageEngineId;
    }

    public synchronized void update(ColumnStatistics statistics) {
        writeHeat += statistics.getWriteHeat();
        readHeat += statistics.getReadHeat();
        totalHeat += statistics.getTotalHeat();
    }

    public double getWriteHeat() {
        return writeHeat;
    }

    public void setWriteHeat(double writeHeat) {
        this.writeHeat = writeHeat;
    }

    public double getReadHeat() {
        return readHeat;
    }

    public void setReadHeat(double readHeat) {
        this.readHeat = readHeat;
    }

    public double getTotalHeat() {
        return totalHeat;
    }

    public void setTotalHeat(double totalHeat) {
        this.totalHeat = totalHeat;
    }

    public long getStorageEngineId() {
        return storageEngineId;
    }

    public void setStorageEngineId(long storageEngineId) {
        this.storageEngineId = storageEngineId;
    }
}
