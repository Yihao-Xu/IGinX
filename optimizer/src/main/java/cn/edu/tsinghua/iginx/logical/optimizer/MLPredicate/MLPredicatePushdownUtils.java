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

import cn.edu.tsinghua.iginx.engine.logical.utils.LogicalFilterUtils;
import cn.edu.tsinghua.iginx.engine.logical.utils.OperatorUtils;
import cn.edu.tsinghua.iginx.engine.physical.memory.execute.utils.ExprUtils;
import cn.edu.tsinghua.iginx.engine.shared.expr.*;
import cn.edu.tsinghua.iginx.engine.shared.operator.Project;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;
import cn.edu.tsinghua.iginx.engine.shared.source.FragmentSource;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.exception.MLPredicateUnsupportedModelException;
import cn.edu.tsinghua.iginx.metadata.IMetaManager;
import cn.edu.tsinghua.iginx.metadata.MetaManagerWrapper;
import cn.edu.tsinghua.iginx.metadata.entity.FragmentMeta;
import cn.edu.tsinghua.iginx.utils.StringUtils;
import java.util.*;
import java.util.stream.Collectors;

public class MLPredicatePushdownUtils {
  private static final StatsManagerMock statsManager = StatsManagerMock.getInstance();

  /** 获取每个底层Project-Fragment对应的列 todo: 之后还需要考虑rename的情况 */
  private static Map<Project, List<String>> getCol2ProjectFragment(
      List<String> cols, List<Project> projects) {
    IMetaManager metaManager = MetaManagerWrapper.getInstance();
    Map<Project, List<String>> res = new HashMap<>();
    projects.forEach(p -> res.put(p, new ArrayList<>()));

    for (String col : cols) {
      FragmentMeta fragment = metaManager.getLatestFragmentByColumnName(col);

      for (Project project : projects) {
        FragmentMeta curFragment = ((FragmentSource) project.getSource()).getFragment();
        if (!curFragment.getMasterStorageUnitId().equals(fragment.getMasterStorageUnitId())) {
          continue;
        }

        for (String pattern : project.getPatterns()) {
          if (StringUtils.match(col, pattern)) {
            res.get(project).add(col);
            break;
          }
        }
      }
    }

    return res;
  }

  /** 生成所有的Project组合 */
  private static List<List<Project>> generateProjectCombinations(List<Project> projects) {
    List<List<Project>> res = new ArrayList<>();
    int n = projects.size();
    int totalCombinations = 1 << n;

    for (int i = 1; i < totalCombinations; i++) {
      List<Project> combination = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        if ((i & (1 << j)) != 0) {
          combination.add(projects.get(j));
        }
      }
      res.add(combination);
    }

    return res;
  }

  public static List<Filter> generateMLPredicate(
      ModelInfo modelInfo,
      cn.edu.tsinghua.iginx.engine.shared.operator.Operator root,
      Filter filter)
      throws MLPredicateUnsupportedModelException {
    switch (modelInfo.getModelType()) {
      case LogicalRegression:
      case LinearRegression:
        return generateRegressionPredicate((RegressionModelInfo) modelInfo, root, filter);
      case DecisionTree:
        return generateDecisionTreePredicate((DecisionTreeModelInfo) modelInfo, root, filter);
      default:
        throw new MLPredicateUnsupportedModelException("The model type is not supported");
    }
  }

  /**
   * 生成线性回归的ML下推谓词
   *
   * @param rmi 线性回归模型信息
   * @param root Operator，用于获取算子底下的Project-Fragment算子
   * @param filter 要处理的ML filter，要求为expr filter,左边为ML函数，右边为常数
   */
  public static List<Filter> generateRegressionPredicate(
      RegressionModelInfo rmi,
      cn.edu.tsinghua.iginx.engine.shared.operator.Operator root,
      Filter filter) {
    List<Filter> res = new ArrayList<>();
    List<String> cols = rmi.cols;
    List<Double> weights = rmi.weights;

    // 生成复合谓词时，将同一个Project-Fragment的列看作一个整体
    List<Project> projectFragmentList = new ArrayList<>();
    OperatorUtils.findProjectOperators(projectFragmentList, root);
    Map<Project, List<String>> project2Cols = getCol2ProjectFragment(cols, projectFragmentList);
    projectFragmentList =
        projectFragmentList.stream()
            .filter(p -> !project2Cols.get(p).isEmpty())
            .collect(Collectors.toList()); // 过滤掉不包含任何谓词列的project
    Map<String, Double> col2Weight = new HashMap<>();
    for (int i = 0; i < cols.size(); i++) {
      col2Weight.put(cols.get(i), weights.get(i + 1));
    }
    double w0 = weights.get(0);

    if (filter.getType() != FilterType.Expr) {
      throw new IllegalArgumentException("The ML filter should be an expr filter");
    }
    ExprFilter exprFilter = (ExprFilter) filter;
    if (exprFilter.getExpressionB().getType() != Expression.ExpressionType.Constant) {
      throw new IllegalArgumentException("The right side of the ML filter should be a constant");
    }
    Object value = ((ConstantExpression) exprFilter.getExpressionB()).getValue();
    Op op = exprFilter.getOp();
    double l;
    if (value.getClass().equals(Integer.class)) {
      l = (double) (int) value;
    } else if (value.getClass().equals(Double.class)) {
      l = (double) value;
    } else if (value.getClass().equals(Long.class)) {
      l = (double) (long) value;
    } else if (value.getClass().equals(Float.class)) {
      l = (double) (float) value;
    } else if (value.getClass().equals(Boolean.class)) {
      l = 0.5;
      op = (boolean) value ? Op.GE : Op.L;
    } else {
      throw new IllegalArgumentException("The right side of the ML filter should be a number");
    }

    //    List<List<Project>> projectCombinations =
    // generateProjectCombinations(projectFragmentList);
    List<List<Project>> projectCombinations =
        projectFragmentList.stream().map(Collections::singletonList).collect(Collectors.toList());

    // 根据每个projects组合生成对应的谓词
    for (List<Project> leftProjects : projectCombinations) {
      List<String> leftCols = new ArrayList<>();
      List<String> rightCols = new ArrayList<>();

      // 归类左侧列和右侧列
      for (Project project : leftProjects) {
        leftCols.addAll(project2Cols.get(project));
      }
      leftCols = new ArrayList<>(new HashSet<>(leftCols));
      for (String col : cols) {
        if (!leftCols.contains(col)) {
          rightCols.add(col);
        }
      }

      if (leftCols.isEmpty()) continue;

      // 构建左侧表达式
      Expression leftExpr = null;
      for (String col : leftCols) {
        Expression curExpr =
            new BinaryExpression(
                new BaseExpression(col),
                new ConstantExpression(col2Weight.get(col)),
                Operator.STAR);
        if (leftExpr == null) {
          leftExpr = curExpr;
        } else {
          leftExpr = new BinaryExpression(leftExpr, curExpr, Operator.PLUS);
        }
      }

      // 计算右侧常量
      double rightConst = l - w0;
      for (String col : rightCols) {
        rightConst -= statsManager.getMin(col) * col2Weight.get(col);
        rightConst -= statsManager.getMax(col) * col2Weight.get(col);
      }
      Expression rightExpr = new ConstantExpression(rightConst);

      // 构建谓词
      Filter newFilter = new ExprFilter(leftExpr, op, rightExpr);
      res.add(newFilter);
    }

    return res;
  }

  /** 生成decision tree的ML下推谓词 */
  public static List<Filter> generateDecisionTreePredicate(
      DecisionTreeModelInfo dmi,
      cn.edu.tsinghua.iginx.engine.shared.operator.Operator root,
      Filter filter) {
    List<Filter> res = new ArrayList<>();

    // 生成复合谓词时，将同一个Project-Fragment的列看作一个整体
    if (filter.getType() != FilterType.Expr)
      throw new IllegalArgumentException("The ML filter should be an expr filter");
    ExprFilter exprFilter = (ExprFilter) filter;
    if (exprFilter.getExpressionB().getType() != Expression.ExpressionType.Constant) {
      throw new IllegalArgumentException("The right side of the ML filter should be a constant");
    }
    if (exprFilter.getExpressionA().getType() != Expression.ExpressionType.Function) {
      throw new IllegalArgumentException("The left side of the ML filter should be a function");
    }

    FuncExpression funcExpression = (FuncExpression) exprFilter.getExpressionA();
    ConstantExpression constantExpression = (ConstantExpression) exprFilter.getExpressionB();
    Op op = exprFilter.getOp();
    if (!Op.isEqualOp(op)) {
      throw new IllegalArgumentException("The operator of the ML filter should be equal");
    }

    // 提取ML模型传入的参数列，将实际列与模型内部列对应起来
    Map<String, String> modelCol2IGinXCol = new HashMap<>();
    for (int i = 0; i < dmi.cols.size(); i++) {
      modelCol2IGinXCol.put(
          dmi.cols.get(i), funcExpression.getExpressions().get(i).getColumnName());
    }

    Object l = constantExpression.getValue();
    List<List<DecisionTreeNode>> paths = dmi.getPathOfValue(l);

    List<Project> projectFragmentList = new ArrayList<>();
    OperatorUtils.findProjectOperators(projectFragmentList, root);
    Map<Project, List<String>> project2Cols = getCol2ProjectFragment(dmi.cols, projectFragmentList);
    //    List<List<Project>> projectCombinations =
    // generateProjectCombinations(projectFragmentList);
    List<List<Project>> projectCombinations =
        projectFragmentList.stream().map(Collections::singletonList).collect(Collectors.toList());

    // 根据每个projects组合生成对应的谓词
    for (List<Project> leftProjects : projectCombinations) {
      final List<String> needCols = new ArrayList<>();
      for (String col : dmi.cols) {
        if (project2Cols.get(leftProjects.get(0)).contains(col)) {
          needCols.add(col);
        }
      }

      // 从各个路径提取需要的条件，路径内filter用and连接，路径间用or连接
      List<Filter> orChildren = new ArrayList<>();
      for (List<DecisionTreeNode> path : paths) {
        AndFilter andFilter = new AndFilter(new ArrayList<>());
        for (DecisionTreeNode node : path) {
          if (node.filter == null) continue;
          Filter curFilter =
              getFiltersFromDecisionTreeNode(node.filter.copy(), needCols, modelCol2IGinXCol);
          if (curFilter.getType() == FilterType.Bool) {
            continue;
          }
          andFilter.getChildren().add(curFilter);
        }
        if (andFilter.getChildren().size() > 0) {
          orChildren =
              orChildren.stream()
                  .filter(f -> !LogicalFilterUtils.cover(andFilter, (AndFilter) f))
                  .collect(Collectors.toList()); // 将当前结果中的被当前and filter覆盖的filter去掉
          if (orChildren.stream()
              .noneMatch(f -> LogicalFilterUtils.cover((AndFilter) f, andFilter))) {
            orChildren.add(andFilter);
          }
        }
      }
      OrFilter orFilter = new OrFilter(orChildren);
      if (orChildren.size() > 0 && res.stream().noneMatch(f -> f.equals(orFilter))) {
        res.add(new OrFilter(orChildren));
      }
    }

    return res;
  }

  /**
   * 给定Filter中提取出与给定行相关的Filter，会修改原filter
   *
   * @param filter 传入的Filter
   * @param needCols 需要的列(IGinX列名)
   * @param modelCol2IGinXCol 模型列名到IGinX列名的映射
   */
  private static Filter getFiltersFromDecisionTreeNode(
      Filter filter, List<String> needCols, Map<String, String> modelCol2IGinXCol) {
    switch (filter.getType()) {
      case And:
        AndFilter andFilter = (AndFilter) filter;
        List<Filter> andChildren = andFilter.getChildren();
        andChildren.replaceAll(f -> getFiltersFromDecisionTreeNode(f, needCols, modelCol2IGinXCol));
        andFilter.setChildren(
            andChildren.stream()
                .filter(f -> f.getType() != FilterType.Bool)
                .collect(Collectors.toList()));
        return andFilter;
      case Or:
        OrFilter orFilter = (OrFilter) filter;
        List<Filter> orChildren = orFilter.getChildren();
        for (int i = 0; i < orFilter.getChildren().size(); i++) {
          orChildren.set(
              i, getFiltersFromDecisionTreeNode(orChildren.get(i), needCols, modelCol2IGinXCol));
          if (orChildren.get(i).getType() == FilterType.Bool) {
            return new BoolFilter(true);
          }
        }
        return orFilter;
      case Expr:
        // 如果ExprFilter中只包含needCols中的列，则直接返回，否则返回true
        ExprFilter exprFilter = (ExprFilter) filter;
        Expression leftExpr = exprFilter.getExpressionA();
        Expression rightExpr = exprFilter.getExpressionB();
        List<String> exprCols = ExprUtils.getPathFromExpr(leftExpr);
        exprCols.addAll(ExprUtils.getPathFromExpr(rightExpr));

        for (String col : exprCols) {
          if (!needCols.contains(modelCol2IGinXCol.get(col))) {
            return new BoolFilter(true);
          }
        }
        return exprFilter;
      case Bool:
        return filter;
      case Value:
        ValueFilter valueFilter = (ValueFilter) filter;
        if (needCols.contains(modelCol2IGinXCol.get(valueFilter.getPath()))) {
          return valueFilter;
        }
        return new BoolFilter(true);
      case Path:
        PathFilter pathFilter = (PathFilter) filter;
        if (needCols.contains(modelCol2IGinXCol.get(pathFilter.getPathA()))
            && needCols.contains(modelCol2IGinXCol.get(pathFilter.getPathB()))) {
          return pathFilter;
        }
        return new BoolFilter(true);
      case Not:
        NotFilter notFilter = (NotFilter) filter;
        notFilter.setChild(
            getFiltersFromDecisionTreeNode(notFilter.getChild(), needCols, modelCol2IGinXCol));
        return notFilter;
      case Key:
        return new BoolFilter(true);
      case In:
        InFilter inFilter = (InFilter) filter;
        if (needCols.contains(modelCol2IGinXCol.get(inFilter.getPath()))) {
          return inFilter;
        }
        return new BoolFilter(true);
      default:
        throw new IllegalArgumentException("The filter type is not supported");
    }
  }
}
