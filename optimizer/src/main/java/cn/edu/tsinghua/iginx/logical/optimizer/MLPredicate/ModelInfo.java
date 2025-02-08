package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;

import java.util.List;

public interface ModelInfo {
    ModelType getModelType();

    List<String> getCols();
}
