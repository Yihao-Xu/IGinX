package cn.edu.tsinghua.iginx.engine.logical.optimizer;

import cn.edu.tsinghua.iginx.engine.shared.operator.filter.AndFilter;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.Filter;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.PathFilter;
import cn.edu.tsinghua.iginx.engine.shared.operator.filter.ValueFilter;
import cn.edu.tsinghua.iginx.metadata.entity.ColumnsInterval;
import cn.edu.tsinghua.iginx.metadata.entity.KeyInterval;

import java.util.*;

import static cn.edu.tsinghua.iginx.engine.logical.utils.ExprUtils.columnRangeContainPath;

public class OperatorSchema {
    private Map<ColumnsInterval, Set<KeyInterval>> columns2KeyInterval;

    private Set<String> patterns;

    /**
     * 空构造函数
     */
    public OperatorSchema(){
        this.columns2KeyInterval = new HashMap<>();
        this.patterns = new HashSet<>();
    }

    /**
     * 基础构造函数
     * @param columns2KeyInterval 列区间到key区间的映射
     * @param patterns Pattern集合
     */
    public OperatorSchema(Map<ColumnsInterval, Set<KeyInterval>> columns2KeyInterval, Set<String> patterns) {
        this.columns2KeyInterval = columns2KeyInterval;
        this.patterns = patterns;
    }

    /**
     * 仅有一个列区间和一个key区间的构造函数
     * @param columnsInterval 列区间
     * @param keyInterval key区间
     * @param patterns Pattern集合
     */
    public OperatorSchema(ColumnsInterval columnsInterval, KeyInterval keyInterval, Set<String> patterns) {
        this.columns2KeyInterval = new HashMap<>();
        this.columns2KeyInterval.put(columnsInterval, new HashSet<>());
        this.columns2KeyInterval.get(columnsInterval).add(keyInterval);
        this.patterns = patterns;
    }

    /**
     * 拷贝构造函数
     * @param os 要拷贝的OperatorSchema
     */
    public OperatorSchema(OperatorSchema os){
        this.columns2KeyInterval = new HashMap<>();
        for (Map.Entry<ColumnsInterval, Set<KeyInterval>> entry : os.getColumns2KeyInterval().entrySet()) {
            this.columns2KeyInterval.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.patterns = new HashSet<>(os.getPatterns());
    }

    /**
     * 合并2个OperatorSchema的构造函数
     * @param os1 OperatorSchema1
     * @param os2 OperatorSchema2
     */
    public OperatorSchema(OperatorSchema os1, OperatorSchema os2){
        this.columns2KeyInterval = new HashMap<>();
        for (Map.Entry<ColumnsInterval, Set<KeyInterval>> entry : os1.getColumns2KeyInterval().entrySet()) {
            this.columns2KeyInterval.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Map.Entry<ColumnsInterval, Set<KeyInterval>> entry : os2.getColumns2KeyInterval().entrySet()) {
            if (this.columns2KeyInterval.containsKey(entry.getKey())) {
                this.columns2KeyInterval.get(entry.getKey()).addAll(entry.getValue());
            } else {
                this.columns2KeyInterval.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        this.patterns = new HashSet<>(os1.getPatterns());
        this.patterns.addAll(os2.getPatterns());
    }

    public Map<ColumnsInterval, Set<KeyInterval>> getColumns2KeyInterval() {
        return columns2KeyInterval;
    }

    public Set<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(Set<String> patterns) {
        this.patterns = patterns;
    }

    public void setColumns2KeyInterval(Map<ColumnsInterval, Set<KeyInterval>> columns2KeyInterval) {
        this.columns2KeyInterval = columns2KeyInterval;
    }

    public void addPatterns(Collection<? extends String> patterns) {
        this.patterns.addAll(patterns);
    }

    public void addPattern(String pattern) {
        this.patterns.add(pattern);
    }

    public void addColumns2KeyInterval(Map<ColumnsInterval, Set<KeyInterval>> columns2KeyInterval) {
        this.columns2KeyInterval.putAll(columns2KeyInterval);
    }

    public void addColumns2KeyInterval(ColumnsInterval columnsInterval, KeyInterval keyInterval) {
        if (this.columns2KeyInterval.containsKey(columnsInterval)) {
            this.columns2KeyInterval.get(columnsInterval).add(keyInterval);
        } else {
            this.columns2KeyInterval.put(columnsInterval, new HashSet<>());
            this.columns2KeyInterval.get(columnsInterval).add(keyInterval);
        }
    }

    /**
     * 综合patterns和columns2KeyInterval，判断path是否在schema中
     * @param path 要判断的path
     * @return path是否在schema中
     */
    public boolean pathInSchema(String path) {
        for (Map.Entry<ColumnsInterval, Set<KeyInterval>> entry : columns2KeyInterval.entrySet()) {
            if (columnRangeContainPath(entry.getKey(), path)) {
                return patterns.contains(path);
            }
        }

        return false;
    }

    /**
     * 综合patterns和columns2KeyInterval，判断filter的Path是否在schema中
     * @param filter 要判断的filter
     * @return filter的Path是否在schema中
     */
    public boolean filterInSchema(Filter filter) {
        switch(filter.getType()){
            case And:
                List<Filter> andChildren = ((AndFilter) filter).getChildren();
                for (Filter child : andChildren) {
                    if (!filterInSchema(child)) {
                        return false;
                    }
                }
                break;
            case Or:
                List<Filter> orChildren = ((AndFilter) filter).getChildren();
                for (Filter child : orChildren) {
                    if (filterInSchema(child)) {
                        return true;
                    }
                }
                break;
            case Not:
                return filterInSchema(((AndFilter) filter).getChildren().get(0));
            case Value:
                String path = ((ValueFilter) filter).getPath();
                return pathInSchema(path);
            case Path:
                String pathA = ((PathFilter) filter).getPathA();
                String pathB = ((PathFilter) filter).getPathB();
                return pathInSchema(pathA) && pathInSchema(pathB);
            default:
                return true;
        }

        return false;
    }

}
