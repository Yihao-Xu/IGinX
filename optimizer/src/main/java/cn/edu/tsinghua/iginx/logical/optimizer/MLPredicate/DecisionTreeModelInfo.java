package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecisionTreeModelInfo {

    public final ModelType modelType;

    public final DecisionTreeNode root;

    public final List<String> cols;

    public DecisionTreeModelInfo(ModelType modelType, DecisionTreeNode root, List<String> cols) {
        this.modelType = modelType;
        this.root = root;
        this.cols = cols;
    }

    /**
     * 获取能通向给定值的路径节点set
     * @param Value 给定值
     * @return 路径节点set，已去重
     */
    public List<List<DecisionTreeNode>> getPathOfValue(Object Value){
        List<List<DecisionTreeNode>> res = new ArrayList<>();

        List<DecisionTreeNode> curPath = new ArrayList<>();

        dfs(root, curPath, Value, res);

        return res;

    }

    private void dfs(DecisionTreeNode curNode, List<DecisionTreeNode> curPath, Object Value, List<List<DecisionTreeNode>> res){
        if(curNode == null){
            return;
        }

        curPath.add(curNode);

        if(curNode.leafValue != null && curNode.leafValue.equals(Value)){
            res.add(new ArrayList<>(curPath));
        }

        dfs(curNode.left, curPath, Value, res);
        dfs(curNode.right, curPath, Value, res);

        curPath.remove(curPath.size() - 1);
    }
}
