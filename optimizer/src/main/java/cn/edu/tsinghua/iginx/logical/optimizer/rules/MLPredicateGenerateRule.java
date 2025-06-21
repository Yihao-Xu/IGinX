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
package cn.edu.tsinghua.iginx.logical.optimizer.rules;

import cn.edu.tsinghua.iginx.engine.logical.utils.LogicalFilterUtils;
import cn.edu.tsinghua.iginx.engine.physical.memory.execute.utils.ExprUtils;
import cn.edu.tsinghua.iginx.engine.shared.expr.Expression;
import cn.edu.tsinghua.iginx.engine.shared.expr.FuncExpression;
import cn.edu.tsinghua.iginx.engine.shared.function.Function;
import cn.edu.tsinghua.iginx.engine.shared.function.manager.FunctionManager;
import cn.edu.tsinghua.iginx.engine.shared.function.udf.python.PyUDTF;
import cn.edu.tsinghua.iginx.engine.shared.operator.InnerJoin;
import cn.edu.tsinghua.iginx.engine.shared.operator.Operator;
import cn.edu.tsinghua.iginx.engine.shared.operator.OuterJoin;
import cn.edu.tsinghua.iginx.engine.shared.operator.Select;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.MLFilter;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.MLPredicatePushdownUtils;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.ModelDataReader;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.ModelInfo;
import cn.edu.tsinghua.iginx.logical.optimizer.core.RuleCall;
import com.google.auto.service.AutoService;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@AutoService(Rule.class)
public class MLPredicateGenerateRule extends Rule {

  private static final Set<Operator> mlPredicateSet =
      new HashSet<>(); // 记录已经处理过的MLPredicate算子，每个只处理一次

  public MLPredicateGenerateRule() {
    /*
     * we want to match the topology like:
     *      Select/InnerJoin/OuterJoin
     *           |
     *          Any
     */
    super("MLPredicatePushDownRule", any());
  }

  @Override
  public boolean matches(RuleCall call) {
    Operator root = call.getMatchedRoot();
    Filter filter = getFilter(root);

    if (filter == null || mlPredicateSet.contains(root)) {
      return false;
    }

    return getMLUDFFromFilter(filter).size() > 0;
  }

  @Override
  public void onMatch(RuleCall call) {
    Operator root = call.getMatchedRoot();
    Filter filter = getFilter(root);
    mlPredicateSet.add(root);

    List<MLFilter> mlFilters = getMLUDFFromFilter(filter);
    for (MLFilter mlFilter : mlFilters) {
      try {
        PyUDTF mlUDF = mlFilter.mlUDF;
        FuncExpression funcExpression = mlFilter.funcExpression;
        String udfInfoStr = mlUDF.getModelInfo();
        ModelInfo modelInfo =
            ModelDataReader.toModelInfo(
                udfInfoStr,
                funcExpression.getExpressions().stream()
                    .map(Expression::getColumnName)
                    .collect(Collectors.toList()));
        List<Filter> generated =
            MLPredicatePushdownUtils.generateMLPredicate(modelInfo, root, mlFilter.filter);

        // 直接将ML谓词加入当前算子的filter中
        generated.add(0, filter);
        setFilter(root, new AndFilter(generated));

        call.transformTo(root);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static List<MLFilter> getMLUDFFromFilter(Filter filter) {
    List<ExprFilter> exprFilters = LogicalFilterUtils.getExprFilters(filter);
    List<MLFilter> mlFilters = new ArrayList<>();
    if (exprFilters.size() == 0) {
      return mlFilters;
    }

    Map<Filter, Filter> parentMap = ExprUtils.getFilterParentMap(filter);

    for (ExprFilter exprFilter : exprFilters) {
      Filter parent = parentMap.get(exprFilter);
      while (parent != null && parent.getType() != FilterType.Or) {
        parent = parentMap.get(parent);
      }
      if (parent != null && parent.getType() == FilterType.Or) {
        continue;
      }

      Expression exprA = exprFilter.getExpressionA(), exprB = exprFilter.getExpressionB();
      Predicate<Expression> isFunc =
          expression ->
              expression.getType() == Expression.ExpressionType.Function
                  && ((FuncExpression) expression).isPyUDF();

      List<Expression> funcExpressionList = new ArrayList<>();
      funcExpressionList.addAll(ExprUtils.getExpressionByPredicate(exprA, isFunc));
      funcExpressionList.addAll(ExprUtils.getExpressionByPredicate(exprB, isFunc));

      FunctionManager fm = FunctionManager.getInstance();
      for (Expression expression : funcExpressionList) {
        FuncExpression funcExpression = (FuncExpression) expression;
        Function udf = fm.getFunction(funcExpression.getFuncName());
        if (udf instanceof PyUDTF && ((PyUDTF) udf).isMLPredicate()) {
          mlFilters.add(new MLFilter(exprFilter, funcExpression, (PyUDTF) udf));
        }
      }
    }

    return mlFilters;
  }

  private Filter getFilter(Operator operator) {
    switch (operator.getType()) {
      case Select:
        return ((Select) operator).getFilter();
      case InnerJoin:
        return ((InnerJoin) operator).getFilter();
      case OuterJoin:
        return ((OuterJoin) operator).getFilter();
      default:
        return null;
    }
  }

  private void setFilter(Operator operator, Filter filter) {
    switch (operator.getType()) {
      case Select:
        ((Select) operator).setFilter(filter);
        break;
      case InnerJoin:
        ((InnerJoin) operator).setFilter(filter);
        break;
      case OuterJoin:
        ((OuterJoin) operator).setFilter(filter);
        break;
    }
  }
}
