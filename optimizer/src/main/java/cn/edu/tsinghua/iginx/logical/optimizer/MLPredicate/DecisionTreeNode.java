package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecisionTreeNode {

    public Filter filter;

    public DecisionTreeNode left;

    public DecisionTreeNode right;

    public Double leafValue;

    public DecisionTreeNode(Filter filter, DecisionTreeNode left, DecisionTreeNode right, Double leafValue) {
        this.filter = filter;
        this.left = left;
        this.right = right;
    }

    public DecisionTreeNode(Filter filter) {
        this(filter, null, null, null);
    }


}
