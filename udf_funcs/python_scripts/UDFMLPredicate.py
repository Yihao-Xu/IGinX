"""
所有UDF ML谓词的抽象父类
"""
from abc import ABC, abstractmethod

import numpy as np


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

    @abstractmethod
    def get_scaler(self):
        """加载标准化器"""

    def get_model_info(self):
        """获取模型信息，以json格式字符串返回"""
        model = self.get_model()
        if model is None:
            raise ValueError("Model is not loaded. Please correctly load a model using get_model().")

        try:
            scaler = self.get_scaler()
            scaler_info = {
                "mean": scaler.mean_.tolist(),
                "scale": scaler.scale_.tolist(),
                "feature_names": list(scaler.feature_names_in_) if hasattr(scaler,"feature_names_in_") else None  # 转换 numpy array
            }
        except Exception as e:
            print(f"Warning: Could not load scaler.pkl, proceeding without normalization info. Error: {e}")
            scaler_info = None  # 如果加载失败，则不包含归一化信息

        # 根据模型的不同类型输出信息：
        if model.__class__.__name__ == "DecisionTreeClassifier":
            tree = model.tree_
            model_info = {
                "type": "DecisionTreeClassifier",
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
                        "feature_index": int(tree.feature[i]),
                        "threshold": float(tree.threshold[i]),
                        "left_child": int(tree.children_left[i]),
                        "right_child": int(tree.children_right[i]),
                        "n_samples": int(tree.n_node_samples[i]),
                        "value": tree.value[i].tolist()
                    }
                else:  # 叶子节点
                    counts = tree.value[i][0]
                    class_index = int(np.argmax(counts))
                    predicted_label = model.classes_[class_index]  # ✅ 转换为实际标签
                    node = {
                        "node": i,
                        "n_samples": int(tree.n_node_samples[i]),
                        "value": predicted_label  # ✅ 返回真实的分类值（如 'S'）
                    }
                model_info["nodes"].append(node)

        elif model.__class__.__name__ == "LinearRegression":
            model_info = {
                "type": "LinearRegression",
                "coef": model.coef_.tolist(),
                "intercept": float(model.intercept_)
            }
        elif model.__class__.__name__ == "LogisticRegression":
            model_info = {
                "type": "LogisticRegression",
                "coef": model.coef_.tolist(),
                "intercept": float(model.intercept_)
            }
        else:
            raise ValueError(f"Unsupported MLPredicate model type: {model.__class__.__name__}")

        if scaler_info:
            model_info["scaler"] = scaler_info

        return model_info.__str__()

