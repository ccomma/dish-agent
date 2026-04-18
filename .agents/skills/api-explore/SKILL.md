# API Exploration Skill

当使用不熟悉的库 API 时，使用此 skill 探索代码库并找到正确的方法签名。

## 触发条件

- 使用新添加的依赖/库
- 遇到 API 方法签名不匹配错误
- 不确定某个类的正确用法

## 探索方法

### 1. Maven JAR 检查
```bash
# 下载并解压 JAR 文件
cd /tmp && mkdir explore && cd explore
unzip -q ~/.m2/repository/{groupId}/{artifactId}/{version}/{artifactId}-{version}.jar

# 检查类文件
javap {package_path}.ClassName
```

### 2. 常用 API 检查点

**LangChain4j 1.12.2 关键类**：
- `EmbeddingModel` - `embed()`, `embedAll()`
- `EmbeddingStore` - `add()`, `search()`
- `EmbeddingSearchRequest` - `builder()`
- `EmbeddingSearchResult` - `matches()`
- `EmbeddingMatch` - `embedded()`, `score()`
- `ScoringModel` - `scoreAll()`
- `ReRankingContentAggregator` - `aggregate()`

### 3. Web 资源
- Maven Central 仓库搜索
- LangChain4j 官方文档

## 执行流程

1. 确定要探索的类/方法
2. 使用 javap 检查类签名
3. 如果需要，探索相关类
4. 返回正确的方法签名和用法

## 输出格式

```
类名: {package}.ClassName

方法:
- methodName(paramType) -> ReturnType
- ...

使用示例:
{code snippet}
```
