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
import cn.edu.tsinghua.iginx.engine.shared.operator.Select;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;
import cn.edu.tsinghua.iginx.engine.shared.source.OperatorSource;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.MLPredicatePushdownUtils;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.ModelDataReader;
import cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate.ModelInfo;
import cn.edu.tsinghua.iginx.logical.optimizer.core.RuleCall;
import cn.edu.tsinghua.iginx.utils.Pair;
import com.google.auto.service.AutoService;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@AutoService(Rule.class)
public class MLPredicateGenerateRule extends Rule {

  private static final Set<Select> mlPredicateSet =
      new HashSet<>(); // 记录已经处理过的MLPredicate算子，每个只处理一次

  public MLPredicateGenerateRule() {
    /*
     * we want to match the topology like:
     *         Select
     *           |
     *          Any
     */
    super("MLPredicatePushDownRule", operand(Select.class, any()));
  }

  @Override
  public boolean matches(RuleCall call) {
    // 当Select节点中的filter含有ML谓词时，返回true
    Select select = (Select) call.getMatchedRoot();
    Filter filter = select.getFilter();

    if (mlPredicateSet.contains(select)) {
      return false;
    }

    return getMLUDFFromFilter(filter).size() > 0;
  }

  @Override
  public void onMatch(RuleCall call) {
    Select select = (Select) call.getMatchedRoot();
    Filter filter = select.getFilter();
    mlPredicateSet.add(select);

    List<Pair<PyUDTF, FuncExpression>> mlUDFs = getMLUDFFromFilter(filter);
    for (Pair<PyUDTF, FuncExpression> pair : mlUDFs) {
      try {
        PyUDTF mlUDF = pair.k;
        FuncExpression funcExpression = pair.v;
        String udfInfoStr = mlUDF.getModelInfo();
        ModelInfo modelInfo =
            ModelDataReader.toModelInfo(
                udfInfoStr,
                funcExpression.getExpressions().stream()
                    .map(Expression::getColumnName)
                    .collect(Collectors.toList()));
        List<Filter> mlFilters =
            MLPredicatePushdownUtils.generateMLPredicate(modelInfo, select, filter);

        // 直接下推，后续交给filter pushdown规则处理
        Select mlSelect = new Select(select.getSource(), new AndFilter(mlFilters), null);
        select.setSource(new OperatorSource(mlSelect));
        call.transformTo(select);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static List<Pair<PyUDTF, FuncExpression>> getMLUDFFromFilter(Filter filter) {
    List<ExprFilter> exprFilters = LogicalFilterUtils.getExprFilters(filter);
    List<Pair<PyUDTF, FuncExpression>> mlUDFs = new ArrayList<>();
    if (exprFilters.size() == 0) {
      return mlUDFs;
    }

    Map<Filter, Filter> parentMap = ExprUtils.getFilterParentMap(filter);
    List<Expression> funcExpressionList = new ArrayList<>();
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
      funcExpressionList.addAll(ExprUtils.getExpressionByPredicate(exprA, isFunc));
      funcExpressionList.addAll(ExprUtils.getExpressionByPredicate(exprB, isFunc));
    }

    FunctionManager fm = FunctionManager.getInstance();
    for (Expression expression : funcExpressionList) {
      FuncExpression funcExpression = (FuncExpression) expression;
      Function udf = fm.getFunction(funcExpression.getFuncName());
      if (udf instanceof PyUDTF && ((PyUDTF) udf).isMLPredicate()) {
        mlUDFs.add(new Pair<>((PyUDTF) udf, funcExpression));
      }
    }

    return mlUDFs;
  }
}
