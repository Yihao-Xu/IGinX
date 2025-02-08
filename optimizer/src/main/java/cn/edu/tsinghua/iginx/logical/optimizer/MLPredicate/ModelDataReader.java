package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;


import cn.edu.tsinghua.iginx.engine.shared.data.Value;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.Op;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.ValueFilter;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.exception.MLPredicateUnsupportedModelException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModelDataReader {

    public static ModelInfo toModelInfo(String modelJson, List<String> cols) throws MLPredicateUnsupportedModelException {
        JSONObject json = JSON.parseObject(modelJson);
        ModelType modelType = toModelType(json.getString("modelType"));

        switch (modelType){
            case DecisionTree:
                return toDecisionTreeModelInfo(json, cols);
            case LogicalRegression:
            case LinearRegression:
                return toRegressionModelInfo(json, cols);
            default:
                throw new MLPredicateUnsupportedModelException("Unsupported ML Predicate model type: " + modelType);
        }
    }

    /**
     * 将回归模型json字符串转换为模型信息
     */
    private static RegressionModelInfo toRegressionModelInfo(JSONObject modelJson, List<String> cols) {
        ModelType modelType = toModelType(modelJson.getString("modelType"));
        List<Double> weights = modelJson.getJSONArray("coefficients").toJavaList(Double.class);

        return new RegressionModelInfo(modelType, cols, weights);
    }


    /**
     * 将决策树模型json字符串转换为模型信息
     */
    private static DecisionTreeModelInfo toDecisionTreeModelInfo(JSONObject modelJson, List<String> cols){
        ModelType modelType = toModelType(modelJson.getString("modelType"));
        List<JSONObject> nodesJson = modelJson.getList("nodes", JSONObject.class);
        List<DecisionTreeNode> nodes =nodesJson.stream()
                .map(nodeJson -> toDecisionTreeNode(nodeJson, cols))
                .collect(Collectors.toList());

        DecisionTreeNode root = nodes.get(0);
        for(int i = 0; i < nodes.size(); i++){
            JSONObject nodeJson = nodesJson.get(i);
            DecisionTreeNode node = nodes.get(i);

            Integer leftIndex = nodeJson.getInteger("left_child");
            Integer rightIndex = nodeJson.getInteger("right_child");
            if(leftIndex != null){
                node.left = nodes.get(leftIndex);
            }
            if(rightIndex != null){
                node.right = nodes.get(rightIndex);
            }
        }


        return new DecisionTreeModelInfo(modelType, root, cols);
    }

    /**
     * 将json字符串转换为决策树节点
     */
    private static DecisionTreeNode toDecisionTreeNode(JSONObject nodeJson, List<String> cols){
        Integer feature = nodeJson.getInteger("feature");
        boolean isLeaf = feature == null;
        if(isLeaf){
            Double leafValue = nodeJson.getDouble("threshold");
            return new DecisionTreeNode(null, null, null, leafValue);
        }else{
            String col = cols.get(feature);
            Double threshold = nodeJson.getDouble("threshold");
            ValueFilter filter = new ValueFilter(col, Op.LE, new Value(threshold));
            // 左右子节点之后在上一层调用函数处理
            return new DecisionTreeNode(filter);
        }
    }

    private static ModelType toModelType(String modelType){
        switch (modelType){
            case "LinearRegression":
                return ModelType.LinearRegression;
            case "LogicalRegression":
                return ModelType.LogicalRegression;
            case "DecisionTree":
                return ModelType.DecisionTree;
            default:
                return ModelType.Unknown;
        }
    }
}
