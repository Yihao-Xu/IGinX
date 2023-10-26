package cn.edu.tsinghua.iginx.engine.logical.optimizer;

import cn.edu.tsinghua.iginx.engine.shared.function.FunctionCall;
import cn.edu.tsinghua.iginx.engine.shared.operator.*;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.*;
import cn.edu.tsinghua.iginx.engine.shared.operator.type.OperatorType;
import cn.edu.tsinghua.iginx.engine.shared.source.FragmentSource;
import cn.edu.tsinghua.iginx.engine.shared.source.OperatorSource;
import cn.edu.tsinghua.iginx.engine.shared.source.Source;
import cn.edu.tsinghua.iginx.engine.shared.source.SourceType;
import cn.edu.tsinghua.iginx.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MagicPushOptimizer implements Optimizer{

    enum SMTCheckResult{
        CANNOT_PUSH_DOWN, // 不能下推
        CAN_PUSH_DOWN, // 可以下推
        CAN_PUSH_DOWN_SUPERSET // 可以超集下推
    }

    private static final Logger logger = LoggerFactory.getLogger(MagicPushOptimizer.class);

    private static MagicPushOptimizer instance = null;

    public static MagicPushOptimizer getInstance() {
        if (instance == null) {
            synchronized (MagicPushOptimizer.class) {
                if (instance == null) {
                    instance = new MagicPushOptimizer();
                }
            }
        }
        return instance;
    }

    @Override
    public Operator optimize(Operator root) {
        return root;
    }

    /**
     * 从输出表的Filter中获取候选的Filter组件
     * @param filter 输出表的Filter
     * @return 候选的Filter List
     */
    private List<Filter> extractComponentsFromF(Filter filter){
        List<Filter> components = new ArrayList<>();
        if (filter == null){
            return components;
        }
        components.add(filter);

        switch(filter.getType()){
            case And:
                AndFilter andFilter = (AndFilter) filter;
                for(Filter f : andFilter.getChildren()){
                    components.addAll(extractComponentsFromF(f));
                }
                break;
            case Or:
                OrFilter orFilter = (OrFilter) filter;
                for(Filter f : orFilter.getChildren()){
                    components.addAll(extractComponentsFromF(f));
                }
                break;
            default:
                break;
        }

        return components;
    }

    /**
     * 从Operator中获取候选的Filter组件（主要是UDF）
     * @param Op Operator
     * @return 候选的Filter List
     */
    private List<Filter> extractComponentsFromOp(Operator Op){
        List<Filter> components = new ArrayList<>();
        // TODO 目前阶段暂不实现
        return components;
    }

    /**
     * 获取以root为根的查询树每个Operator的输出Schema，输入Schema即为子节点的输出Schema
     * @param root 查询树的根节点
     * @param outSchemaMap 每个Operator的映射到输出Schema的Map
     */
    private void getOutSchemaMap(Operator root, Map<Operator, OperatorSchema> outSchemaMap){
        List<OperatorSchema> inSchemaList = new ArrayList<>();

        if(OperatorType.isUnaryOperator(root.getType())){
            UnaryOperator unaryOp = (UnaryOperator) root;
            Source source = unaryOp.getSource();
            OperatorSchema inSchema = null;
            if (source.getType() == SourceType.Fragment) {
                // 如果子节点是FragmentSource，直接从上面获取输入Schema
                FragmentSource fragmentSource = (FragmentSource) source;
                inSchema = new OperatorSchema();
                inSchema.addColumns2KeyInterval(fragmentSource.getFragment().getColumnsInterval(), fragmentSource.getFragment().getKeyInterval());
                inSchemaList.add(inSchema);
            } else{
                // 否则先递归计算子节点的输出Schema
                Operator child = ((OperatorSource)unaryOp.getSource()).getOperator();
                getOutSchemaMap(child, outSchemaMap);
                inSchemaList.add(outSchemaMap.get(child));
            }
        }else if(OperatorType.isBinaryOperator(root.getType())) {
            BinaryOperator binaryOp = (BinaryOperator) root;
            Operator childA = ((OperatorSource)binaryOp.getSourceA()).getOperator();
            Operator childB = ((OperatorSource)binaryOp.getSourceB()).getOperator();
            getOutSchemaMap(childA, outSchemaMap);
            getOutSchemaMap(childB, outSchemaMap);
        }else if(OperatorType.isMultipleOperator(root.getType())){
            MultipleOperator multipleOp = (MultipleOperator) root;
            for(Source source: multipleOp.getSources()){
                Operator child = ((OperatorSource)source).getOperator();
                getOutSchemaMap(child, outSchemaMap);
            }
        }

        inSchemaList = getInSchemaList(root, outSchemaMap);
        OperatorSchema outSchema = getOutSchema(root, inSchemaList);
        outSchemaMap.put(root, outSchema);
    }

    private OperatorSchema getOutSchema(Operator op, List<OperatorSchema> inSchemaList){
        OperatorSchema outSchema = new OperatorSchema();
        for(OperatorSchema inSchema: inSchemaList){
            outSchema.addColumns2KeyInterval(inSchema.getColumns2KeyInterval());
            outSchema.addPatterns(inSchema.getPatterns());
        }

        switch(op.getType()){
            case Project:
                // Project算子直接将输出Schema的Patterns设置为Project算子的Patterns
                Project projectOp = (Project) op;
                List<String> patterns = projectOp.getPatterns();
                outSchema.setPatterns(new HashSet<>(patterns));
                break;
            case GroupBy:
                // GroupBy增加其生成的新列
                GroupBy groupByOp = (GroupBy) op;
                List<FunctionCall> functionCallList = groupByOp.getFunctionCallList();
                for(FunctionCall functionCall: functionCallList){
                    String functionName = functionCall.getFunction().getIdentifier();
                    List<String> paths = functionCall.getParams().getPaths();
                    for(String path: paths) {
                        String pattern = functionName + "(" + path + ")";
                        outSchema.addPattern(pattern);
                    }
                }
                break;
            case Rename:
                // Rename将原列名替换为新列名
                // TODO: 带通配符的重命名较复杂，有空再做
                Rename renameOp = (Rename) op;
                Map<String, String> aliasMap = renameOp.getAliasMap();
                for(Map.Entry<String, String> entry: aliasMap.entrySet()){
                    String oldAlias = entry.getKey();
                    String newAlias = entry.getValue();
                    if(outSchema.getPatterns().contains(oldAlias)){
                        outSchema.getPatterns().remove(oldAlias);
                        outSchema.getPatterns().add(newAlias);
                    }
                }
                break;
            case AddSchemaPrefix:
                // AddSchemaPrefix要给Pattern增加前缀
                // TODO：一般的查询里不会有，有空再做
                break;
            default:
                // 其他算子的输出Schema不变，就是单纯将输入Schema并起来。
                break;
        }

        return outSchema;
    }

    /**
     * 获取Operator的输入Schema（即所有子节点的输出Schema）
     * @param op Operator
     * @param outSchemaMap 每个Operator的映射到输出Schema的Map
     * @return 输入Schema列表
     */
    private List<OperatorSchema> getInSchemaList(Operator op, Map<Operator, OperatorSchema> outSchemaMap){
        List<OperatorSchema> inSchemaList = new ArrayList<>();
        List<Operator> childList = new ArrayList<>();
        if(OperatorType.isUnaryOperator(op.getType()) ){
            UnaryOperator unaryOp = (UnaryOperator) op;
            Operator child = ((OperatorSource)unaryOp.getSource()).getOperator();
            childList.add(child);
        } else if (OperatorType.isBinaryOperator(op.getType())) {
            BinaryOperator binaryOp = (BinaryOperator) op;
            Operator childA = ((OperatorSource)binaryOp.getSourceA()).getOperator();
            Operator childB = ((OperatorSource)binaryOp.getSourceB()).getOperator();
            childList.add(childA);
            childList.add(childB);
        }else if(OperatorType.isMultipleOperator(op.getType())){
            MultipleOperator multipleOp = (MultipleOperator) op;
            for(Source source: multipleOp.getSources()){
                Operator child = ((OperatorSource)source).getOperator();
                childList.add(child);
            }
        }

        for(Operator child: childList){
            if(outSchemaMap.containsKey(child)) {
                inSchemaList.add(outSchemaMap.get(child));
            }else{
                logger.error("Operator {} not in outSchemaMap", child);
            }
        }

        return inSchemaList;
    }

    /**
     * 根据Operator生成输出表到输入表的列名映射
     * @param operator Operator
     * @return <输出表列,输入表列>的列名映射
     */
    private Map<String, String> buildColumnsMapByOperator(Operator operator){
        Map<String, String> columnsMap = new HashMap<>();

        switch(operator.getType()){
            case Project:
                break;

            case GroupBy:
                GroupBy groupByOp = (GroupBy) operator;
                List<FunctionCall> functionCallList = groupByOp.getFunctionCallList();
                for(FunctionCall functionCall: functionCallList){
                    String functionName = functionCall.getFunction().getIdentifier();
                    List<String> paths = functionCall.getParams().getPaths();
                    for(String path: paths) {
                        String alias = functionName + "(" + path + ")";
                        columnsMap.put(alias, path);
                    }
                }

                break;

            case Rename:
                Rename renameOp = (Rename) operator;
                columnsMap.putAll(renameOp.getAliasMap());
                break;

            case InnerJoin:
                InnerJoin innerJoinOp = (InnerJoin) operator;
                break;

            case AddSchemaPrefix:
                break;

            case Join:
                Join joinOp = (Join) operator;
                break;

            default:
                break;
        }

        return columnsMap;
    }

    /**
     * 从候选的Filter组件中生成候选的Filter
     * @param F 输出表的Filter
     * @param Op Operator
     * @return Pair<候选的Filter, 是否是超集下推>
     */
    private Pair<Filter, SMTCheckResult> generateCandidateFilter(Filter F, Operator Op, Map<Operator, OperatorSchema> outSchemaMap){
        OperatorSchema outSchema = new OperatorSchema();
        List<OperatorSchema> inSchemaList = getInSchemaList(Op, outSchemaMap);

        List<Filter> componentsFromF = extractComponentsFromF(replaceNewPathWithF(F, buildColumnsMapByOperator(Op)));
        List<Filter> componentsFromOp = extractComponentsFromOp(Op);

        Filter G = null;

        if(!componentsFromF.isEmpty() && componentsFromOp.isEmpty()){
            G = componentsFromF.get(0);
        }else if(componentsFromF.isEmpty() && !componentsFromOp.isEmpty()) {
            // TODO: componentsFromOp暂未实现
        }

        SMTCheckResult smtCheckResult = SMTCheck(F, Op, G);
        if(smtCheckResult != SMTCheckResult.CANNOT_PUSH_DOWN) {
            return new Pair<>(G, smtCheckResult);
        }

        List<Filter> components = new ArrayList<>();
        components.addAll(componentsFromF);
        components.addAll(componentsFromOp);

        List<Filter> candidateList = new ArrayList<>();
        List<Filter> conjunctionList = new ArrayList<>();
        List<List<Filter>> componentsSubsetList = generateAllSubsets(components);

        for(List<Filter> subset: componentsSubsetList){
            if(!hasConflictLiteral(subset)){
                conjunctionList.add(new AndFilter(subset));
            }
        }

        conjunctionList.sort(new Comparator<Filter>() {
            @Override
            public int compare(Filter o1, Filter o2) {
                // 降序排序
                return getFilterLiteralCount(o2) - getFilterLiteralCount(o1);
            }
        });

        for(Filter conjunction: conjunctionList){
            if(!dupicated(candidateList, conjunction)){
                candidateList.add(conjunction);
                smtCheckResult = SMTCheck(F, Op, conjunction);
                if(smtCheckResult != SMTCheckResult.CANNOT_PUSH_DOWN){
                    return new Pair<>(conjunction, smtCheckResult);
                }
            }
        }

        return new Pair<>(null, SMTCheckResult.CANNOT_PUSH_DOWN);
    }

    /**
     * 判断Filter组件列表中是否存在冲突的字面量
     * @param subset Filter组件列表
     * @return 是否存在冲突的字面量
     */
    private boolean hasConflictLiteral(List<Filter> subset){
        // TODO
        return false;
    }

    /**
     * SMT正确性验证
     * @param F 输出表的Filter
     * @param Op Operator
     * @param G 输入表的Filter
     * @return SMT正确性验证结果
     */
    private SMTCheckResult SMTCheck(Filter F, Operator Op, Filter G){
        // TODO: SMT 正确性验证尚未实现
        return SMTCheckResult.CAN_PUSH_DOWN_SUPERSET;
    }

    /**
     * 将Filter中的路径替换为新的路径
     * @param F Filter
     * @param columnsMap <输出表列,输入表列>的列名映射
     * @return 替换后的Filter
     */
    private Filter replaceNewPathWithF(Filter F, Map<String, String> columnsMap){
        switch(F.getType()){
            case And:
                AndFilter andFilter = (AndFilter) F;
                List<Filter> children = andFilter.getChildren();
                for(int i = 0; i < children.size(); i++){
                    children.set(i, replaceNewPathWithF(children.get(i), columnsMap));
                }
                break;
            case Or:
                OrFilter orFilter = (OrFilter) F;
                children = orFilter.getChildren();
                for(int i = 0; i < children.size(); i++){
                    children.set(i, replaceNewPathWithF(children.get(i), columnsMap));
                }
                break;
            case Not:
                OrFilter notFilter = (OrFilter) F;
                children = notFilter.getChildren();
                children.set(0, replaceNewPathWithF(children.get(0), columnsMap));
                break;
            case Value:
                ValueFilter valueFilter = (ValueFilter) F;
                String path = valueFilter.getPath();
                if(columnsMap.containsKey(path)){
                    return new ValueFilter(columnsMap.get(path), valueFilter.getOp(), valueFilter.getValue());
                }
                break;
            case Path:
                PathFilter pathFilter = (PathFilter) F;
                String pathA = pathFilter.getPathA();
                String pathB = pathFilter.getPathB();
                if(columnsMap.containsKey(pathA) || columnsMap.containsKey(pathB)){
                    return new PathFilter(columnsMap.getOrDefault(pathA, pathA), pathFilter.getOp(), columnsMap.getOrDefault(pathB, pathB));
                }
                break;
            default:
                break;
        }

        return F;
    }

    /**
     * 生成components的所有子集
     * @param components Filter组件列表
     * @return components的所有子集
     */
    private List<List<Filter>> generateAllSubsets(List<Filter> components){
        int n = components.size();
        List<List<Filter>> subsetList = new ArrayList<>();

        for(int i = 0; i < (1 << n); i++){
            List<Filter> subset = new ArrayList<>();
            for(int j = 0; j < n; j++){
                if((i & (1 << j)) != 0){
                    subset.add(components.get(j));
                }
            }
            subsetList.add(subset);
        }

        return subsetList;
    }

    /**
     * 通过filter.toString(),判断candidate是否在candidateList中
     * @param candidateList 候选的Filter List
     * @param candidate 要判断的Filter
     * @return 是否在candidateList中
     */
    private boolean dupicated(List<Filter> candidateList, Filter candidate){
        for(Filter filter: candidateList){
            if(filter.toString().equals(candidate.toString())){
                return true;
            }
        }
        return false;
    }

    /**
     * 获取Filter中的字面量个数
     * @param filter Filter
     * @return 字面量个数
     */
    private int getFilterLiteralCount(Filter filter){
        int count = 0;
        switch(filter.getType()){
            case And:
                AndFilter andFilter = (AndFilter) filter;
                for(Filter f: andFilter.getChildren()){
                    count += getFilterLiteralCount(f);
                }
                break;
            case Or:
                OrFilter orFilter = (OrFilter) filter;
                for(Filter f: orFilter.getChildren()){
                    count += getFilterLiteralCount(f);
                }
                break;
            case Not:
                count += getFilterLiteralCount(((OrFilter) filter).getChildren().get(0));
                break;
            default:
                count++;
                break;
        }
        return count;
    }
}
