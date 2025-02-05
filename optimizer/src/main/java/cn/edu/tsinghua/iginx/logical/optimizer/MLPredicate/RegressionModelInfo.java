package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;

import java.util.List;

/**
 * 用于存储线性/逻辑回归模型的信息
 */
public class RegressionModelInfo {

    public final ModelType modelType;

    public final List<String> cols;

    public final List<Double> weights;

    public RegressionModelInfo(ModelType modelType, List<String> cols, List<Double> weights) {
        this.modelType = modelType;
        this.cols = cols;
        this.weights = weights;
    }
}
