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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DecisionTreeModelInfo implements ModelInfo {

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
   *
   * @param value 给定值
   * @return 路径节点set，已去重
   */
  public List<List<DecisionTreeNode>> getPathOfValue(String value) {
    List<List<DecisionTreeNode>> res = new ArrayList<>();

    List<DecisionTreeNode> curPath = new ArrayList<>();

    dfs(root, curPath, value, res);

    return res;
  }

  private void dfs(
      DecisionTreeNode curNode,
      List<DecisionTreeNode> curPath,
      String value,
      List<List<DecisionTreeNode>> res) {
    if (curNode == null) {
      return;
    }

    curPath.add(curNode);

    if (curNode.leafValue != null && curNode.leafValue.equals(value)) {
      res.add(new ArrayList<>(curPath));
    }

    dfs(curNode.left, curPath, value, res);
    dfs(curNode.right, curPath, value, res);

    curPath.remove(curPath.size() - 1);
  }

  @Override
  public ModelType getModelType() {
    return modelType;
  }

  @Override
  public List<String> getCols() {
    return cols;
  }
}
