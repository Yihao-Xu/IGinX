/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 * TSIGinX@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;

import cn.edu.tsinghua.iginx.engine.shared.data.Value;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.Op;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.ValueFilter;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.exception.MLPredicateUnsupportedModelException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModelDataReader {

  public static ModelInfo toModelInfo(String modelJson, List<String> cols)
      throws MLPredicateUnsupportedModelException {
    JSONObject json = JSON.parseObject(modelJson);
    ModelType modelType = toModelType(json.getString("type"));

    switch (modelType) {
      case DecisionTree:
        return toDecisionTreeModelInfo(json, cols);
      case LogicalRegression:
      case LinearRegression:
        return toRegressionModelInfo(json, cols);
      default:
        throw new MLPredicateUnsupportedModelException(
            "Unsupported ML Predicate model type: " + modelType);
    }
  }

  /** 将回归模型json字符串转换为模型信息 */
  private static RegressionModelInfo toRegressionModelInfo(
      JSONObject modelJson, List<String> cols) {
    ModelType modelType = toModelType(modelJson.getString("type"));
    List<Double> weights = new ArrayList<>(modelJson.getJSONArray("coef").toJavaList(Double.class));
    weights.add(0, modelJson.getDouble("intercept"));
    return new RegressionModelInfo(modelType, cols, weights);
  }

  /** 将决策树模型json字符串转换为模型信息 */
  private static DecisionTreeModelInfo toDecisionTreeModelInfo(
      JSONObject modelJson, List<String> cols) {
    ModelType modelType = toModelType(modelJson.getString("type"));
    List<JSONObject> nodesJson = modelJson.getList("nodes", JSONObject.class);
    List<DecisionTreeNode> nodes =
        nodesJson.stream()
            .map(nodeJson -> toDecisionTreeNode(nodeJson, cols))
            .collect(Collectors.toList());

    DecisionTreeNode root = nodes.get(0);
    for (int i = 0; i < nodes.size(); i++) {
      JSONObject nodeJson = nodesJson.get(i);
      DecisionTreeNode node = nodes.get(i);

      Integer leftIndex = nodeJson.getInteger("left_child");
      Integer rightIndex = nodeJson.getInteger("right_child");
      if (leftIndex != null) {
        node.left = nodes.get(leftIndex);
      }
      if (rightIndex != null) {
        node.right = nodes.get(rightIndex);
      }
    }

    return new DecisionTreeModelInfo(modelType, root, cols);
  }

  /** 将json字符串转换为决策树节点 */
  private static DecisionTreeNode toDecisionTreeNode(JSONObject nodeJson, List<String> cols) {
    Integer feature = nodeJson.getInteger("feature_index");
    boolean isLeaf = feature == null;
    if (isLeaf) {
      BigDecimal leafValue = nodeJson.getJSONArray("value").getJSONArray(0).getBigDecimal(0);
      return new DecisionTreeNode(null, null, null, leafValue);
    } else {
      String col = cols.get(feature);
      Double threshold = nodeJson.getDouble("threshold");
      ValueFilter filter = new ValueFilter(col, Op.LE, new Value(threshold));
      // 左右子节点之后在上一层调用函数处理
      return new DecisionTreeNode(filter);
    }
  }

  private static ModelType toModelType(String modelType) {
    switch (modelType) {
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
