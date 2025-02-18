#
# IGinX - the polystore system with high performance
# Copyright (C) Tsinghua University
# TSIGinX@gmail.com
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

"""
所有UDF ML谓词的抽象父类
"""
from abc import ABC, abstractmethod


class UDFMLPredicate(ABC):
    def __init__(self):
        self.model = None

    @abstractmethod
    def get_model(self):
        """从给定的文件路径加载模型"""

    @abstractmethod
    def transform(self, data, *args, **kwargs):
        """将加载的模型应用于输入数据"""

    @abstractmethod
    def buildHeader(self, data):
        """构建结果表头"""

    def get_model_info(self):
        """获取模型信息，以json格式字符串返回"""
        model = self.get_model()
        if model is None:
            raise ValueError("Model is not loaded. Please correctly load a model using get_model().")
        # 根据模型的不同类型输出信息：
        if model.__class__.__name__ == "DecisionTreeRegressor":
            tree = model.tree_
            model_info = {
                "type": "DecisionTree",
                "max_depth": model.max_depth,
                "feature_importances": model.feature_importances_.tolist(),
                "n_features": model.n_features_in_,
                "n_outputs": model.n_outputs_,
                "n_nodes": tree.node_count,
                "nodes": []
            }
            for i in range(tree.node_count):
                if tree.children_left[i] != tree.children_right[i]:  # 判断是否为内部节点
                    node = {
                        "node": i,
                        "feature_index": tree.feature[i],
                        "threshold": tree.threshold[i],
                        "left_child": tree.children_left[i],
                        "right_child": tree.children_right[i],
                        "n_samples": tree.n_node_samples[i],
                        "value": tree.value[i].tolist()
                    }
                else:  # 叶子节点
                    node = {
                        "node": i,
                        "n_samples": tree.n_node_samples[i],
                        "value": tree.value[i].tolist()
                    }
                model_info["nodes"].append(node)

        elif model.__class__.__name__ == "LinearRegression":
            model_info = {
                "type": "LinearRegression",
                "coef": model.coef_.tolist(),
                "intercept": model.intercept_
            }
        elif model.__class__.__name__ == "LogisticRegression":
            model_info = {
                "type": "LogisticRegression",
                "coef": model.coef_.tolist(),
                "intercept": model.intercept_
            }
        else:
            raise ValueError(f"Unsupported MLPredicate model type: {model.__class__.__name__}")
        return model_info.__str__()

