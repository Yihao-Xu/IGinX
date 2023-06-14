/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.edu.tsinghua.iginx.metadata.cache;

import cn.edu.tsinghua.iginx.conf.Config;
import cn.edu.tsinghua.iginx.conf.ConfigDescriptor;
import cn.edu.tsinghua.iginx.engine.shared.data.write.*;
import cn.edu.tsinghua.iginx.metadata.entity.*;
import cn.edu.tsinghua.iginx.metadata.statistics.ColumnStatistics;
import cn.edu.tsinghua.iginx.metadata.statistics.ColumnsIntervalStatistics;
import cn.edu.tsinghua.iginx.metadata.statistics.IginxStatistics;
import cn.edu.tsinghua.iginx.metadata.statistics.StorageEngineStatistics;
import cn.edu.tsinghua.iginx.policy.simple.ColumnCalDO;
import cn.edu.tsinghua.iginx.sql.statement.InsertStatement;
import cn.edu.tsinghua.iginx.thrift.DataType;
import cn.edu.tsinghua.iginx.utils.Pair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMetaCache implements IMetaCache {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMetaCache.class.getName());

    private static final Config config = ConfigDescriptor.getInstance().getConfig();

    private static DefaultMetaCache INSTANCE = null;

    // 分片列表的缓存
    private final List<Pair<ColumnsRange, List<FragmentMeta>>> sortedFragmentMetaLists;

    private final Map<ColumnsRange, List<FragmentMeta>> fragmentMetaListMap;

    private final List<FragmentMeta> dummyFragments;

    private int fragmentCacheSize;

    private final int fragmentCacheMaxSize;

    private final boolean enableFragmentCacheControl = config.isEnableMetaCacheControl();

    private long minKey = 0L;

    private final ReadWriteLock fragmentLock;

    // 数据单元的缓存
    private final Map<String, StorageUnitMeta> storageUnitMetaMap;

    // 已有数据对应的数据单元
    private final Map<String, StorageUnitMeta> dummyStorageUnitMetaMap;

    private final ReadWriteLock storageUnitLock;

    // iginx 的缓存
    private final Map<Long, IginxMeta> iginxMetaMap;

    // 数据后端的缓存
    private final Map<Long, StorageEngineMeta> storageEngineMetaMap;

    // schemaMapping 的缓存
    private final Map<String, Map<String, Integer>> schemaMappings;

    // user 的缓存
    private final Map<String, UserMeta> userMetaMap;

    // 序列信息版本号的缓存
    private final Map<Integer, Integer> columnsVersionMap;

    private final ReadWriteLock insertRecordLock = new ReentrantReadWriteLock();

    private final Map<String, ColumnCalDO> columnCalDOConcurrentHashMap = new ConcurrentHashMap<>();

    private final Random random = new Random();

    // transform task 的缓存
    private final Map<String, TransformTaskMeta> transformTaskMetaMap;

    // 统计信息的缓存
    private final Map<Long, IginxStatistics> activeIginxStatisticsMap;

    private final Set<String> activeSeparatorSet;

    private final Map<Long, StorageEngineStatistics> activeStorageEngineStatisticsMap;

    private final Map<String, ColumnStatistics> activeColumnStatisticsMap;

    private final Map<ColumnsInterval, ColumnsIntervalStatistics>
            activeColumnsIntervalStatisticsMap;

    private DefaultMetaCache() {
        if (enableFragmentCacheControl) {
            long sizeOfFragment = FragmentMeta.sizeOf();
            fragmentCacheMaxSize =
                    (int) ((long) (config.getFragmentCacheThreshold() * 1024) / sizeOfFragment);
            fragmentCacheSize = 0;
        } else {
            fragmentCacheMaxSize = -1;
        }

        // 分片相关
        sortedFragmentMetaLists = new ArrayList<>();
        fragmentMetaListMap = new HashMap<>();
        dummyFragments = new ArrayList<>();
        fragmentLock = new ReentrantReadWriteLock();
        // 数据单元相关
        storageUnitMetaMap = new HashMap<>();
        dummyStorageUnitMetaMap = new HashMap<>();
        storageUnitLock = new ReentrantReadWriteLock();
        // iginx 相关
        iginxMetaMap = new ConcurrentHashMap<>();
        // 数据后端相关
        storageEngineMetaMap = new ConcurrentHashMap<>();
        // schemaMapping 相关
        schemaMappings = new ConcurrentHashMap<>();
        // user 相关
        userMetaMap = new ConcurrentHashMap<>();
        // 时序列信息版本号相关
        columnsVersionMap = new ConcurrentHashMap<>();
        // transform task 相关
        transformTaskMetaMap = new ConcurrentHashMap<>();
        // 统计信息相关
        activeIginxStatisticsMap = new ConcurrentHashMap<>();
        activeSeparatorSet = new ConcurrentSkipListSet<>();
        activeStorageEngineStatisticsMap = new ConcurrentHashMap<>();
        activeColumnStatisticsMap = new ConcurrentHashMap<>();
        activeColumnsIntervalStatisticsMap = new ConcurrentHashMap<>();
    }

    public static DefaultMetaCache getInstance() {
        if (INSTANCE == null) {
            synchronized (DefaultMetaCache.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DefaultMetaCache();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public boolean enableFragmentCacheControl() {
        return enableFragmentCacheControl;
    }

    @Override
    public long getFragmentMinKey() {
        return minKey;
    }

    private static List<Pair<ColumnsRange, List<FragmentMeta>>> searchFragmentSeriesList(
            List<Pair<ColumnsRange, List<FragmentMeta>>> fragmentSeriesList,
            ColumnsRange tsInterval) {
        List<Pair<ColumnsRange, List<FragmentMeta>>> resultList = new ArrayList<>();
        if (fragmentSeriesList.isEmpty()) {
            return resultList;
        }
        int index = 0;
        while (index < fragmentSeriesList.size()
                && !fragmentSeriesList.get(index).k.isCompletelyAfter(tsInterval)) {
            if (fragmentSeriesList.get(index).k.isIntersect(tsInterval)) {
                resultList.add(fragmentSeriesList.get(index));
            }
            index++;
        }
        return resultList;
    }

    private static List<Pair<ColumnsRange, List<FragmentMeta>>> searchFragmentSeriesList(
            List<Pair<ColumnsRange, List<FragmentMeta>>> fragmentSeriesList, String tsName) {
        List<Pair<ColumnsRange, List<FragmentMeta>>> resultList = new ArrayList<>();
        if (fragmentSeriesList.isEmpty()) {
            return resultList;
        }
        int index = 0;
        while (index < fragmentSeriesList.size()
                && !fragmentSeriesList.get(index).k.isAfter(tsName)) {
            if (fragmentSeriesList.get(index).k.isContain(tsName)) {
                resultList.add(fragmentSeriesList.get(index));
            }
            index++;
        }
        return resultList;
    }

    private static List<FragmentMeta> searchFragmentList(
            List<FragmentMeta> fragmentMetaList, KeyInterval keyInterval) {
        List<FragmentMeta> resultList = new ArrayList<>();
        if (fragmentMetaList.isEmpty()) {
            return resultList;
        }
        int index = 0;
        while (index < fragmentMetaList.size()
                && !fragmentMetaList.get(index).getKeyInterval().isAfter(keyInterval)) {
            if (fragmentMetaList.get(index).getKeyInterval().isIntersect(keyInterval)) {
                resultList.add(fragmentMetaList.get(index));
            }
            index++;
        }
        return resultList;
    }

    private static List<FragmentMeta> searchFragmentList(
            List<FragmentMeta> fragmentMetaList, String storageUnitId) {
        List<FragmentMeta> resultList = new ArrayList<>();
        if (fragmentMetaList.isEmpty()) {
            return resultList;
        }
        for (FragmentMeta meta : fragmentMetaList) {
            if (meta.getMasterStorageUnitId().equals(storageUnitId)) {
                resultList.add(meta);
            }
        }
        return resultList;
    }

    @Override
    public void initFragment(Map<ColumnsRange, List<FragmentMeta>> fragmentListMap) {
        storageUnitLock.readLock().lock();
        fragmentListMap
                .values()
                .forEach(
                        e ->
                                e.forEach(
                                        f ->
                                                f.setMasterStorageUnit(
                                                        storageUnitMetaMap.get(
                                                                f.getMasterStorageUnitId()))));
        storageUnitLock.readLock().unlock();
        fragmentLock.writeLock().lock();
        sortedFragmentMetaLists.addAll(
                fragmentListMap
                        .entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new Pair<>(e.getKey(), e.getValue()))
                        .collect(Collectors.toList()));
        fragmentListMap.forEach(fragmentMetaListMap::put);
        if (enableFragmentCacheControl) {
            // 统计分片总数
            fragmentCacheSize = sortedFragmentMetaLists.stream().mapToInt(e -> e.v.size()).sum();
            while (fragmentCacheSize > fragmentCacheMaxSize) {
                kickOffHistoryFragment();
            }
        }
        fragmentLock.writeLock().unlock();
    }

    private void kickOffHistoryFragment() {
        long nextMinKey = 0L;
        for (List<FragmentMeta> fragmentList : fragmentMetaListMap.values()) {
            FragmentMeta fragment = fragmentList.get(0);
            if (fragment.getKeyInterval().getStartKey() == minKey) {
                fragmentList.remove(0);
                nextMinKey = fragment.getKeyInterval().getEndKey();
                fragmentCacheSize--;
            }
        }
        if (nextMinKey == 0L || nextMinKey == Long.MAX_VALUE) {
            logger.error("unexpected next min key " + nextMinKey + "!");
            System.exit(-1);
        }
        minKey = nextMinKey;
    }

    @Override
    public void addFragment(FragmentMeta fragmentMeta) {
        fragmentLock.writeLock().lock();
        // 更新 fragmentMetaListMap
        List<FragmentMeta> fragmentMetaList =
                fragmentMetaListMap.computeIfAbsent(
                        fragmentMeta.getColumnsRange(), v -> new ArrayList<>());
        if (fragmentMetaList.size() == 0) {
            // 更新 sortedFragmentMetaLists
            updateSortedFragmentsList(fragmentMeta.getColumnsRange(), fragmentMetaList);
        }
        fragmentMetaList.add(fragmentMeta);
        if (enableFragmentCacheControl) {
            if (fragmentMeta.getKeyInterval().getStartKey() < minKey) {
                minKey = fragmentMeta.getKeyInterval().getStartKey();
            }
            fragmentCacheSize++;
            while (fragmentCacheSize > fragmentCacheMaxSize) {
                kickOffHistoryFragment();
            }
        }

        fragmentLock.writeLock().unlock();
    }

    private void updateSortedFragmentsList(
            ColumnsRange tsInterval, List<FragmentMeta> fragmentMetas) {
        Pair<ColumnsRange, List<FragmentMeta>> pair = new Pair<>(tsInterval, fragmentMetas);
        if (sortedFragmentMetaLists.size() == 0) {
            sortedFragmentMetaLists.add(pair);
            return;
        }
        int left = 0, right = sortedFragmentMetaLists.size() - 1;
        while (left <= right) {
            int mid = (left + right) / 2;
            ColumnsRange midTsInterval = sortedFragmentMetaLists.get(mid).k;
            if (tsInterval.compareTo(midTsInterval) < 0) {
                right = mid - 1;
            } else if (tsInterval.compareTo(midTsInterval) > 0) {
                left = mid + 1;
            } else {
                throw new RuntimeException("unexpected fragment");
            }
        }
        if (left == sortedFragmentMetaLists.size()) {
            sortedFragmentMetaLists.add(pair);
        } else {
            sortedFragmentMetaLists.add(left, pair);
        }
    }

    @Override
    public void updateFragment(FragmentMeta fragmentMeta) {
        fragmentLock.writeLock().lock();
        // 更新 fragmentMetaListMap
        List<FragmentMeta> fragmentMetaList =
                fragmentMetaListMap.get(fragmentMeta.getColumnsRange());
        fragmentMetaList.set(fragmentMetaList.size() - 1, fragmentMeta);
        fragmentLock.writeLock().unlock();
    }

    @Override
    public void updateFragmentByColumnsInterval(
            ColumnsRange tsInterval, FragmentMeta fragmentMeta) {
        fragmentLock.writeLock().lock();
        try {
            // 更新 fragmentMetaListMap
            List<FragmentMeta> fragmentMetaList = fragmentMetaListMap.get(tsInterval);
            fragmentMetaList.set(fragmentMetaList.size() - 1, fragmentMeta);
            fragmentMetaListMap.put(fragmentMeta.getColumnsRange(), fragmentMetaList);
            fragmentMetaListMap.remove(tsInterval);

            for (Pair<ColumnsRange, List<FragmentMeta>> columnsIntervalListPair :
                    sortedFragmentMetaLists) {
                if (columnsIntervalListPair.getK().equals(tsInterval)) {
                    columnsIntervalListPair.k = fragmentMeta.getColumnsRange();
                }
            }
        } finally {
            fragmentLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteFragmentByColumnsInterval(
            ColumnsRange tsInterval, FragmentMeta fragmentMeta) {
        fragmentLock.writeLock().lock();
        try {
            // 更新 fragmentMetaListMap
            List<FragmentMeta> fragmentMetaList = fragmentMetaListMap.get(tsInterval);
            fragmentMetaList.remove(fragmentMeta);
            if (fragmentMetaList.size() == 0) {
                fragmentMetaListMap.remove(tsInterval);
            }
            for (int index = 0; index < sortedFragmentMetaLists.size(); index++) {
                if (sortedFragmentMetaLists.get(index).getK().equals(tsInterval)) {
                    sortedFragmentMetaLists.get(index).getV().remove(fragmentMeta);
                    if (sortedFragmentMetaLists.get(index).getV().isEmpty()) {
                        sortedFragmentMetaLists.remove(index);
                    }
                    break;
                }
            }
        } finally {
            fragmentLock.writeLock().unlock();
        }
    }

    @Override
    public Map<ColumnsRange, List<FragmentMeta>> getFragmentMapByColumnsInterval(
            ColumnsRange tsInterval) {
        Map<ColumnsRange, List<FragmentMeta>> resultMap = new HashMap<>();
        fragmentLock.readLock().lock();
        searchFragmentSeriesList(sortedFragmentMetaLists, tsInterval)
                .forEach(e -> resultMap.put(e.k, e.v));
        fragmentLock.readLock().unlock();
        return resultMap;
    }

    @Override
    public List<FragmentMeta> getDummyFragmentsByColumnsInterval(ColumnsRange tsInterval) {
        fragmentLock.readLock().lock();
        List<FragmentMeta> results = new ArrayList<>();
        for (FragmentMeta fragmentMeta : dummyFragments) {
            if (fragmentMeta.isValid() && fragmentMeta.getColumnsRange().isIntersect(tsInterval)) {
                results.add(fragmentMeta);
            }
        }
        fragmentLock.readLock().unlock();
        return results;
    }

    @Override
    public Map<ColumnsRange, FragmentMeta> getLatestFragmentMap() {
        Map<ColumnsRange, FragmentMeta> latestFragmentMap = new HashMap<>();
        fragmentLock.readLock().lock();
        sortedFragmentMetaLists
                .stream()
                .map(e -> e.v.get(e.v.size() - 1))
                .filter(e -> e.getKeyInterval().getEndKey() == Long.MAX_VALUE)
                .forEach(e -> latestFragmentMap.put(e.getColumnsRange(), e));
        fragmentLock.readLock().unlock();
        return latestFragmentMap;
    }

    @Override
    public Map<ColumnsRange, FragmentMeta> getLatestFragmentMapByColumnsInterval(
            ColumnsRange tsInterval) {
        Map<ColumnsRange, FragmentMeta> latestFragmentMap = new HashMap<>();
        fragmentLock.readLock().lock();
        searchFragmentSeriesList(sortedFragmentMetaLists, tsInterval)
                .stream()
                .map(e -> e.v.get(e.v.size() - 1))
                .filter(e -> e.getKeyInterval().getEndKey() == Long.MAX_VALUE)
                .forEach(e -> latestFragmentMap.put(e.getColumnsRange(), e));
        fragmentLock.readLock().unlock();
        return latestFragmentMap;
    }

    @Override
    public Map<ColumnsRange, List<FragmentMeta>> getFragmentMapByColumnsIntervalAndKeyInterval(
            ColumnsRange tsInterval, KeyInterval keyInterval) {
        Map<ColumnsRange, List<FragmentMeta>> resultMap = new HashMap<>();
        fragmentLock.readLock().lock();
        searchFragmentSeriesList(sortedFragmentMetaLists, tsInterval)
                .forEach(
                        e -> {
                            List<FragmentMeta> fragmentMetaList =
                                    searchFragmentList(e.v, keyInterval);
                            if (!fragmentMetaList.isEmpty()) {
                                resultMap.put(e.k, fragmentMetaList);
                            }
                        });
        fragmentLock.readLock().unlock();
        return resultMap;
    }

    @Override
    public List<FragmentMeta> getDummyFragmentsByColumnsIntervalAndKeyInterval(
            ColumnsRange tsInterval, KeyInterval keyInterval) {
        fragmentLock.readLock().lock();
        List<FragmentMeta> results = new ArrayList<>();
        for (FragmentMeta fragmentMeta : dummyFragments) {
            if (fragmentMeta.isValid()
                    && fragmentMeta.getColumnsRange().isIntersect(tsInterval)
                    && fragmentMeta.getKeyInterval().isIntersect(keyInterval)) {
                results.add(fragmentMeta);
            }
        }
        fragmentLock.readLock().unlock();
        return results;
    }

    @Override
    public List<FragmentMeta> getFragmentListByColumnName(String tsName) {
        List<FragmentMeta> resultList;
        fragmentLock.readLock().lock();
        resultList =
                searchFragmentSeriesList(sortedFragmentMetaLists, tsName)
                        .stream()
                        .map(e -> e.v)
                        .flatMap(List::stream)
                        .sorted(
                                (o1, o2) -> {
                                    if (o1.getColumnsRange().getStartColumn() == null
                                            && o2.getColumnsRange().getStartColumn() == null)
                                        return 0;
                                    else if (o1.getColumnsRange().getStartColumn() == null)
                                        return -1;
                                    else if (o2.getColumnsRange().getStartColumn() == null)
                                        return 1;
                                    return o1.getColumnsRange()
                                            .getStartColumn()
                                            .compareTo(o2.getColumnsRange().getStartColumn());
                                })
                        .collect(Collectors.toList());
        fragmentLock.readLock().unlock();
        return resultList;
    }

    @Override
    public FragmentMeta getLatestFragmentByColumnName(String tsName) {
        FragmentMeta result;
        fragmentLock.readLock().lock();
        result =
                searchFragmentSeriesList(sortedFragmentMetaLists, tsName)
                        .stream()
                        .map(e -> e.v)
                        .flatMap(List::stream)
                        .filter(e -> e.getKeyInterval().getEndKey() == Long.MAX_VALUE)
                        .findFirst()
                        .orElse(null);
        fragmentLock.readLock().unlock();
        return result;
    }

    @Override
    public List<FragmentMeta> getFragmentMapByExactColumnsInterval(ColumnsRange tsInterval) {
        List<FragmentMeta> res = fragmentMetaListMap.getOrDefault(tsInterval, new ArrayList<>());
        // 对象不匹配的情况需要手动匹配（?）
        if (res.size() == 0) {
            for (Map.Entry<ColumnsRange, List<FragmentMeta>> fragmentMetaListEntry :
                    fragmentMetaListMap.entrySet()) {
                if (fragmentMetaListEntry.getKey().toString().equals(tsInterval.toString())) {
                    return fragmentMetaListEntry.getValue();
                }
            }
        }
        return res;
    }

    @Override
    public List<FragmentMeta> getFragmentListByColumnNameAndKeyInterval(
            String tsName, KeyInterval keyInterval) {
        List<FragmentMeta> resultList;
        fragmentLock.readLock().lock();
        List<FragmentMeta> fragmentMetas =
                searchFragmentSeriesList(sortedFragmentMetaLists, tsName)
                        .stream()
                        .map(e -> e.v)
                        .flatMap(List::stream)
                        .sorted(Comparator.comparingLong(o -> o.getKeyInterval().getStartKey()))
                        .collect(Collectors.toList());
        resultList = searchFragmentList(fragmentMetas, keyInterval);
        fragmentLock.readLock().unlock();
        return resultList;
    }

    @Override
    public List<FragmentMeta> getFragmentListByStorageUnitId(String storageUnitId) {
        List<FragmentMeta> resultList;
        fragmentLock.readLock().lock();
        List<FragmentMeta> fragmentMetas =
                sortedFragmentMetaLists
                        .stream()
                        .map(e -> e.v)
                        .flatMap(List::stream)
                        .sorted(Comparator.comparingLong(o -> o.getKeyInterval().getStartKey()))
                        .collect(Collectors.toList());
        resultList = searchFragmentList(fragmentMetas, storageUnitId);
        fragmentLock.readLock().unlock();
        return resultList;
    }

    @Override
    public boolean hasFragment() {
        return !sortedFragmentMetaLists.isEmpty() || (enableFragmentCacheControl && minKey != 0L);
    }

    @Override
    public boolean hasStorageUnit() {
        return !storageUnitMetaMap.isEmpty();
    }

    @Override
    public void initStorageUnit(Map<String, StorageUnitMeta> storageUnits) {
        storageUnitLock.writeLock().lock();
        for (StorageUnitMeta storageUnit : storageUnits.values()) {
            storageUnitMetaMap.put(storageUnit.getId(), storageUnit);
            getStorageEngine(storageUnit.getStorageEngineId()).addStorageUnit(storageUnit);
        }
        storageUnitLock.writeLock().unlock();
    }

    @Override
    public StorageUnitMeta getStorageUnit(String id) {
        StorageUnitMeta storageUnit;
        storageUnitLock.readLock().lock();
        storageUnit = storageUnitMetaMap.get(id);
        if (storageUnit == null) {
            storageUnit = dummyStorageUnitMetaMap.get(id);
        }
        storageUnitLock.readLock().unlock();
        return storageUnit;
    }

    @Override
    public Map<String, StorageUnitMeta> getStorageUnits(Set<String> ids) {
        Map<String, StorageUnitMeta> resultMap = new HashMap<>();
        storageUnitLock.readLock().lock();
        for (String id : ids) {
            StorageUnitMeta storageUnit = storageUnitMetaMap.get(id);
            if (storageUnit != null) {
                resultMap.put(id, storageUnit);
            } else {
                storageUnit = dummyStorageUnitMetaMap.get(id);
                if (storageUnit != null) {
                    resultMap.put(id, storageUnit);
                }
            }
        }
        storageUnitLock.readLock().unlock();
        return resultMap;
    }

    @Override
    public List<StorageUnitMeta> getStorageUnits() {
        List<StorageUnitMeta> storageUnitMetaList;
        storageUnitLock.readLock().lock();
        storageUnitMetaList = new ArrayList<>(storageUnitMetaMap.values());
        storageUnitMetaList.addAll(dummyStorageUnitMetaMap.values());
        storageUnitLock.readLock().unlock();
        return storageUnitMetaList;
    }

    @Override
    public void addStorageUnit(StorageUnitMeta storageUnitMeta) {
        storageUnitLock.writeLock().lock();
        storageUnitMetaMap.put(storageUnitMeta.getId(), storageUnitMeta);
        storageUnitLock.writeLock().unlock();
    }

    @Override
    public void updateStorageUnit(StorageUnitMeta storageUnitMeta) {
        storageUnitLock.writeLock().lock();
        storageUnitMetaMap.put(storageUnitMeta.getId(), storageUnitMeta);
        storageUnitLock.writeLock().unlock();
    }

    @Override
    public List<IginxMeta> getIginxList() {
        return new ArrayList<>(iginxMetaMap.values());
    }

    @Override
    public void addIginx(IginxMeta iginxMeta) {
        iginxMetaMap.put(iginxMeta.getId(), iginxMeta);
    }

    @Override
    public void removeIginx(long id) {
        iginxMetaMap.remove(id);
    }

    @Override
    public void addStorageEngine(StorageEngineMeta storageEngineMeta) {
        storageUnitLock.writeLock().lock();
        fragmentLock.writeLock().lock();
        if (!storageEngineMetaMap.containsKey(storageEngineMeta.getId())) {
            storageEngineMetaMap.put(storageEngineMeta.getId(), storageEngineMeta);
            if (storageEngineMeta.isHasData()) {
                StorageUnitMeta dummyStorageUnit = storageEngineMeta.getDummyStorageUnit();
                FragmentMeta dummyFragment = storageEngineMeta.getDummyFragment();
                dummyFragment.setMasterStorageUnit(dummyStorageUnit);
                dummyStorageUnitMetaMap.put(dummyStorageUnit.getId(), dummyStorageUnit);
                dummyFragments.add(dummyFragment);
            }
        }
        fragmentLock.writeLock().unlock();
        storageUnitLock.writeLock().unlock();
    }

    @Override
    public boolean updateStorageEngine(long storageID, StorageEngineMeta storageEngineMeta) {
        storageUnitLock.writeLock().lock();
        fragmentLock.writeLock().lock();

        if (!storageEngineMetaMap.containsKey(storageID)) {
            logger.error("No corresponding storage engine needs to be updated");
            return false;
        }
        String dummyStorageUnitID = StorageUnitMeta.generateDummyStorageUnitID(storageID);
        boolean ifOriHasData = storageEngineMetaMap.get(storageID).isHasData();
        if (storageEngineMeta.isHasData()) { // 设置相关元数据信息
            StorageUnitMeta dummyStorageUnit = storageEngineMeta.getDummyStorageUnit();
            FragmentMeta dummyFragment = storageEngineMeta.getDummyFragment();
            dummyFragment.setMasterStorageUnit(dummyStorageUnit);
            dummyStorageUnitMetaMap.put(dummyStorageUnit.getId(), dummyStorageUnit);
            if (ifOriHasData) { // 更新 dummyFragments 数据
                dummyFragments.removeIf(e -> e.getMasterStorageUnitId().equals(dummyStorageUnitID));
            } else {
                dummyFragments.add(dummyFragment);
            }
        } else if (ifOriHasData) { // 原来没有，则移除
            dummyFragments.removeIf(e -> e.getMasterStorageUnitId().equals(dummyStorageUnitID));
            dummyStorageUnitMetaMap.remove(dummyStorageUnitID);
        }
        storageEngineMetaMap.put(storageEngineMeta.getId(), storageEngineMeta);

        fragmentLock.writeLock().unlock();
        storageUnitLock.writeLock().unlock();
        return true;
    }

    @Override
    public List<StorageEngineMeta> getStorageEngineList() {
        return new ArrayList<>(this.storageEngineMetaMap.values());
    }

    @Override
    public StorageEngineMeta getStorageEngine(long id) {
        return this.storageEngineMetaMap.get(id);
    }

    @Override
    public List<FragmentMeta> getFragments() {
        List<FragmentMeta> fragments = new ArrayList<>();
        this.fragmentLock.readLock().lock();
        for (Pair<ColumnsRange, List<FragmentMeta>> pair : sortedFragmentMetaLists) {
            fragments.addAll(pair.v);
        }
        this.fragmentLock.readLock().unlock();
        return fragments;
    }

    @Override
    public Map<String, Integer> getSchemaMapping(String schema) {
        if (this.schemaMappings.get(schema) == null) return null;
        return new HashMap<>(this.schemaMappings.get(schema));
    }

    @Override
    public int getSchemaMappingItem(String schema, String key) {
        Map<String, Integer> schemaMapping = schemaMappings.get(schema);
        if (schemaMapping == null) {
            return -1;
        }
        return schemaMapping.getOrDefault(key, -1);
    }

    @Override
    public void removeSchemaMapping(String schema) {
        schemaMappings.remove(schema);
    }

    @Override
    public void removeSchemaMappingItem(String schema, String key) {
        Map<String, Integer> schemaMapping = schemaMappings.get(schema);
        if (schemaMapping != null) {
            schemaMapping.remove(key);
        }
    }

    @Override
    public void addOrUpdateSchemaMapping(String schema, Map<String, Integer> schemaMapping) {
        Map<String, Integer> mapping =
                schemaMappings.computeIfAbsent(schema, e -> new ConcurrentHashMap<>());
        mapping.putAll(schemaMapping);
    }

    @Override
    public void addOrUpdateSchemaMappingItem(String schema, String key, int value) {
        Map<String, Integer> mapping =
                schemaMappings.computeIfAbsent(schema, e -> new ConcurrentHashMap<>());
        mapping.put(key, value);
    }

    @Override
    public void addOrUpdateUser(UserMeta userMeta) {
        userMetaMap.put(userMeta.getUsername(), userMeta);
    }

    @Override
    public void removeUser(String username) {
        userMetaMap.remove(username);
    }

    @Override
    public List<UserMeta> getUser() {
        return userMetaMap.values().stream().map(UserMeta::copy).collect(Collectors.toList());
    }

    @Override
    public List<UserMeta> getUser(List<String> usernames) {
        List<UserMeta> users = new ArrayList<>();
        for (String username : usernames) {
            UserMeta user = userMetaMap.get(username);
            if (user != null) {
                users.add(user.copy());
            }
        }
        return users;
    }

    @Override
    public void timeSeriesIsUpdated(int node, int version) {
        columnsVersionMap.put(node, version);
    }

    @Override
    public void saveColumnsData(InsertStatement statement) {
        insertRecordLock.writeLock().lock();
        long now = System.currentTimeMillis();

        RawData data = statement.getRawData();
        List<String> paths = data.getPaths();
        if (data.isColumnData()) {
            DataView view =
                    new ColumnDataView(data, 0, data.getPaths().size(), 0, data.getKeys().size());
            for (int i = 0; i < view.getPathNum(); i++) {
                long minn = Long.MAX_VALUE;
                long maxx = Long.MIN_VALUE;
                long totalByte = 0L;
                int count = 0;
                BitmapView bitmapView = view.getBitmapView(i);
                for (int j = 0; j < view.getKeySize(); j++) {
                    if (bitmapView.get(j)) {
                        minn = Math.min(minn, view.getKey(j));
                        maxx = Math.max(maxx, view.getKey(j));
                        if (view.getDataType(i) == DataType.BINARY) {
                            totalByte += ((byte[]) view.getValue(i, j)).length;
                        } else {
                            totalByte += transDatatypeToByte(view.getDataType(i));
                        }
                        count++;
                    }
                }
                if (count > 0) {
                    updateColumnCalDOConcurrentHashMap(
                            paths.get(i), now, minn, maxx, totalByte, count);
                }
            }
        } else {
            DataView view =
                    new RowDataView(data, 0, data.getPaths().size(), 0, data.getKeys().size());
            long[] totalByte = new long[view.getPathNum()];
            int[] count = new int[view.getPathNum()];
            long[] minn = new long[view.getPathNum()];
            long[] maxx = new long[view.getPathNum()];
            Arrays.fill(minn, Long.MAX_VALUE);
            Arrays.fill(maxx, Long.MIN_VALUE);

            for (int i = 0; i < view.getKeySize(); i++) {
                BitmapView bitmapView = view.getBitmapView(i);
                int index = 0;
                for (int j = 0; j < view.getPathNum(); j++) {
                    if (bitmapView.get(j)) {
                        minn[j] = Math.min(minn[j], view.getKey(i));
                        maxx[j] = Math.max(maxx[j], view.getKey(i));
                        if (view.getDataType(j) == DataType.BINARY) {
                            totalByte[j] += ((byte[]) view.getValue(i, index)).length;
                        } else {
                            totalByte[j] += transDatatypeToByte(view.getDataType(j));
                        }
                        count[j]++;
                        index++;
                    }
                }
            }
            for (int i = 0; i < count.length; i++) {
                if (count[i] > 0) {
                    updateColumnCalDOConcurrentHashMap(
                            paths.get(i), now, minn[i], maxx[i], totalByte[i], count[i]);
                }
            }
        }
        insertRecordLock.writeLock().unlock();
    }

    private void updateColumnCalDOConcurrentHashMap(
            String path, long now, long minn, long maxx, long totalByte, int count) {
        ColumnCalDO columnCalDO = new ColumnCalDO();
        columnCalDO.setColumn(path);
        if (columnCalDOConcurrentHashMap.containsKey(path)) {
            columnCalDO = columnCalDOConcurrentHashMap.get(path);
        }
        columnCalDO.merge(now, minn, maxx, count, totalByte);
        columnCalDOConcurrentHashMap.put(path, columnCalDO);
    }

    private long transDatatypeToByte(DataType dataType) {
        switch (dataType) {
            case BOOLEAN:
                return 1;
            case INTEGER:
            case FLOAT:
                return 4;
            case LONG:
            case DOUBLE:
                return 8;
            default:
                return 0;
        }
    }

    @Override
    public List<ColumnCalDO> getMaxValueFromColumns() {
        insertRecordLock.readLock().lock();
        List<ColumnCalDO> ret =
                columnCalDOConcurrentHashMap
                        .values()
                        .stream()
                        .filter(e -> random.nextDouble() < config.getCachedTimeseriesProb())
                        .collect(Collectors.toList());
        insertRecordLock.readLock().unlock();
        return ret;
    }

    @Override
    public double getSumFromColumns() {
        insertRecordLock.readLock().lock();
        double ret =
                columnCalDOConcurrentHashMap
                        .values()
                        .stream()
                        .mapToDouble(ColumnCalDO::getValue)
                        .sum();
        insertRecordLock.readLock().unlock();
        return ret;
    }

    @Override
    public Map<Integer, Integer> getColumnsVersionMap() {
        return columnsVersionMap;
    }

    @Override
    public void addOrUpdateTransformTask(TransformTaskMeta transformTask) {
        transformTaskMetaMap.put(transformTask.getName(), transformTask);
    }

    @Override
    public void dropTransformTask(String name) {
        transformTaskMetaMap.remove(name);
    }

    @Override
    public TransformTaskMeta getTransformTask(String name) {
        return transformTaskMetaMap.getOrDefault(name, null);
    }

    @Override
    public List<TransformTaskMeta> getTransformTasks() {
        return transformTaskMetaMap
                .values()
                .stream()
                .map(TransformTaskMeta::copy)
                .collect(Collectors.toList());
    }

    @Override
    public void addOrUpdateActiveIginxStatistics(
            long id, Map<Long, StorageEngineStatistics> statisticsMap) {
        double totalHeat =
                statisticsMap.values().stream().mapToDouble(StorageEngineStatistics::getHeat).sum();
        activeIginxStatisticsMap.put(id, new IginxStatistics(totalHeat));
    }

    @Override
    public Map<Long, IginxStatistics> getActiveIginxStatistics() {
        return new HashMap<>(activeIginxStatisticsMap);
    }

    @Override
    public void clearActiveIginxStatistics() {
        activeIginxStatisticsMap.clear();
    }

    @Override
    public double getMinActiveIginxStatistics() {
        return activeIginxStatisticsMap
                .values()
                .stream()
                .filter(x -> x.getHeat() != 0.0)
                .mapToDouble(IginxStatistics::getHeat)
                .min()
                .orElse(0.0);
    }

    @Override
    public void addOrUpdateActiveSeparatorSet(Set<String> separators) {
        activeSeparatorSet.addAll(separators);
    }

    @Override
    public Set<String> getActiveSeparatorSet() {
        return new TreeSet<>(activeSeparatorSet);
    }

    @Override
    public void clearActiveSeparatorSet() {
        activeSeparatorSet.clear();
    }

    @Override
    public void addOrUpdateActiveStorageEngineStatistics(
            Map<Long, StorageEngineStatistics> statisticsMap) {
        statisticsMap.forEach(
                (k, v) ->
                        activeStorageEngineStatisticsMap
                                .computeIfAbsent(k, e -> new StorageEngineStatistics())
                                .updateByStorageEngineStatistics(v));
    }

    @Override
    public Map<Long, StorageEngineStatistics> getActiveStorageEngineStatistics() {
        return new ConcurrentHashMap<>(activeStorageEngineStatisticsMap);
    }

    @Override
    public void clearActiveStorageEngineStatistics() {
        activeStorageEngineStatisticsMap.clear();
    }

    @Override
    public void addOrUpdateActiveColumnStatistics(Map<String, ColumnStatistics> statisticsMap) {
        // 更新本地的 activeColumnStatisticsMap
        statisticsMap.forEach(
                (k, v) ->
                        activeColumnStatisticsMap
                                .computeIfAbsent(k, e -> new ColumnStatistics())
                                .update(v));
        // 更新本地的 activeStorageEngineStatisticsMap
        for (ColumnStatistics statistics : statisticsMap.values()) {
            long storageEngineId = statistics.getStorageEngineId();
            if (activeStorageEngineStatisticsMap.containsKey(storageEngineId)) {
                activeStorageEngineStatisticsMap
                        .get(storageEngineId)
                        .updateByTimeSeriesStatistics(statistics);
            } else {
                // TODO 未考虑存储引擎的能力
                activeStorageEngineStatisticsMap.put(
                        storageEngineId,
                        new StorageEngineStatistics(
                                statistics.getTotalHeat(), 1.0, statistics.getTotalHeat()));
            }
        }
    }

    @Override
    public Map<String, ColumnStatistics> getActiveColumnStatistics() {
        return new TreeMap<>(activeColumnStatisticsMap);
    }

    @Override
    public void clearActiveColumnStatistics() {
        activeColumnStatisticsMap.clear();
    }

    @Override
    public void addOrUpdateActiveColumnsIntervalStatistics(
            Map<ColumnsInterval, ColumnsIntervalStatistics> statisticsMap) {
        statisticsMap.forEach(
                (key, value) ->
                        activeColumnsIntervalStatisticsMap
                                .computeIfAbsent(key, e -> new ColumnsIntervalStatistics())
                                .update(value));
    }

    @Override
    public Map<ColumnsInterval, ColumnsIntervalStatistics> getActiveColumnsIntervalStatistics() {
        return new TreeMap<>(activeColumnsIntervalStatisticsMap);
    }

    @Override
    public void clearActiveColumnsIntervalStatistics() {
        activeColumnsIntervalStatisticsMap.clear();
    }

    @Override
    public Set<String> separateActiveColumnsStatisticsByHeat(
            double heat, Map<String, ColumnStatistics> statisticsMap) {
        Set<String> separators = new TreeSet<>();
        double tempSum = 0.0;
        String tempColumn = null;

        for (Map.Entry<String, ColumnStatistics> entry : statisticsMap.entrySet()) {
            double currHeat = entry.getValue().getTotalHeat();
            if (tempSum + currHeat >= heat) {
                if (tempSum + currHeat - heat > heat - tempSum && tempColumn != null) {
                    separators.add(tempColumn);
                    tempSum = currHeat;
                    if (tempSum >= heat) {
                        separators.add(entry.getKey());
                        tempSum = 0.0;
                    }
                } else {
                    separators.add(entry.getKey());
                    tempSum = 0.0;
                }
            } else {
                tempSum += currHeat;
            }
            tempColumn = entry.getKey();
        }

        return separators;
    }

    @Override
    public Map<ColumnsInterval, ColumnsIntervalStatistics>
            separateActiveColumnsStatisticsBySeparators(
                    Map<String, ColumnStatistics> statisticsMap, Set<String> separators) {
        Map<ColumnsInterval, ColumnsIntervalStatistics> columnsIntervalStatisticsMap =
                new TreeMap<>();
        String prevColumn = null;
        String nextColumn = null;
        double tempSum = 0.0;
        boolean needToGetNext = true;
        int tempCount = 0;
        Iterator<Map.Entry<String, ColumnStatistics>> it = statisticsMap.entrySet().iterator();
        Map.Entry<String, ColumnStatistics> currEntry = null;

        for (String separator : separators) {
            nextColumn = separator;
            while (it.hasNext()) {
                if (needToGetNext) {
                    currEntry = it.next();
                }
                if (currEntry == null || currEntry.getKey().compareTo(separator) >= 0) {
                    break;
                } else {
                    tempSum += currEntry.getValue().getTotalHeat();
                    needToGetNext = true;
                    tempCount++;
                }
            }
            columnsIntervalStatisticsMap.put(
                    new ColumnsInterval(prevColumn, nextColumn),
                    new ColumnsIntervalStatistics(tempSum));
            prevColumn = nextColumn;
            tempSum = 0.0;
            if (tempCount == 0) {
                needToGetNext = false;
            }
            tempCount = 0;
        }
        while (it.hasNext()) {
            currEntry = it.next();
            tempSum += currEntry.getValue().getTotalHeat();
        }
        columnsIntervalStatisticsMap.put(
                new ColumnsInterval(nextColumn, null), new ColumnsIntervalStatistics(tempSum));

        return columnsIntervalStatisticsMap;
    }
}
