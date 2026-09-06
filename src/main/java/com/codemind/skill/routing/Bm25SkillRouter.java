package com.codemind.skill.routing;

import com.codemind.skill.SkillDefinition;
import com.codemind.skill.SkillRouteDto;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BM25 混合技能路由器：Lucene BM25 语义打分 + 关键词精确匹配兜底。
 *
 * <p>架构：
 * <ul>
 *   <li><b>Tier 1 关键词兜底</b> — KeywordSkillRouter 做确定性精确匹配（置信度 1.0），优先级最高；
 *   <li><b>Tier 2 BM25 语义路由</b> — 对每个 Skill 建一个 mini Lucene index（doc = description + triggerKeywords），
 *       用 StandardAnalyzer 分词后算 BM25 分；高于阈值直接激活，低于阈值进入候选列表。
 * </ul>
 *
 * <p>为什么用 BM25 而不是纯 TF-IDF：BM25 对长文档有长度归一化（fieldLengthNorm），
 * 并且引入了 k1（词频饱和）和 b（长度归一化系数）两个超参，在短文本检索（skill routing）场景下效果显著优于朴素匹配。
 */
public class Bm25SkillRouter extends SkillRouter {

    private static final Logger log = LoggerFactory.getLogger(Bm25SkillRouter.class);

    /** BM25 自动激活阈值 */
    private static final double AUTO_ACTIVATE_THRESHOLD = 0.65;

    /** BM25 候选阈值（低于此值返回 null） */
    private static final double CANDIDATE_THRESHOLD = 0.3;

    /** 分析器：StandardAnalyzer 对中英混合文本支持良好 */
    private final Analyzer analyzer = new StandardAnalyzer();

    /** 索引目录（内存，进程生命周期内有效） */
    private final ByteBuffersDirectory indexDir = new ByteBuffersDirectory();

    /** 当前 searcher（索引构建后更新） */
    private volatile SearcherManager searcherManager;

    /** 所有 skill（用于兜底关键词路由） */
    private final List<SkillDefinition> skills;

    /** 关键词路由（Tier 1 兜底） */
    private final KeywordSkillRouter keywordRouter;

    public Bm25SkillRouter(List<SkillDefinition> skills) {
        super(skills);
        this.skills = skills != null ? skills : List.of();
        this.keywordRouter = new KeywordSkillRouter(this.skills);
        buildIndex();
    }

    /**
     * 重建 BM25 索引（当技能列表变化时调用）
     */
    public void rebuildIndex() {
        buildIndex();
    }

    /**
     * 路由用户输入到最佳技能。
     *
     * 流程：
     * 1. 先走 KeywordSkillRouter（精确匹配，置信度 1.0，O(N) 字符串比较）
     * 2. 再走 BM25 语义打分（模糊匹配，置信度 0.0~1.0）
     * 3. BM25 分高于阈值 → 直接激活
     * 4. 低于阈值但高于候选阈值 → 返回最佳候选供确认
     * 5. 都不满足 → null（无匹配）
     */
    @Override
    public SkillRouteDto route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        String lower = userInput.toLowerCase().trim();

        // Tier 1: 关键词精确匹配（最快，置信度恒为 1.0）
        SkillRouteDto keywordResult = keywordRouter.route(userInput);
        if (keywordResult != null) {
            log.debug("BM25路由: Tier1 关键词匹配成功 (技能: {}, 关键词: {})",
                    keywordResult.skill().getName(), keywordResult.matchedKeyword());
            return keywordResult;
        }

        // Tier 2: BM25 语义打分
        List<Bm25Score> scores = bm25ScoreAll(lower);

        if (scores.isEmpty()) {
            log.debug("BM25路由: 无 BM25 匹配");
            return null;
        }

        Bm25Score best = scores.get(0);
        double normalizedConfidence = normalizeBm25Score(best.score);

        if (normalizedConfidence >= AUTO_ACTIVATE_THRESHOLD) {
            log.debug("BM25路由: 语义匹配激活 (技能: {}, BM25分: {:.3f}, 置信度: {:.2f})",
                    best.skill.getName(), best.score, normalizedConfidence);
            return SkillRouteDto.semanticMatch(best.skill, "bm25:" + best.skill.getName(), normalizedConfidence);
        }

        if (normalizedConfidence >= CANDIDATE_THRESHOLD) {
            log.info("BM25路由: 语义匹配置信度不足，返回候选 (技能: {}, 置信度: {:.2f})",
                    best.skill.getName(), normalizedConfidence);
            return SkillRouteDto.semanticMatch(best.skill, "bm25:candidate:" + best.skill.getName(), normalizedConfidence);
        }

        log.debug("BM25路由: BM25分过低，无匹配 (最佳: {}, 分: {:.3f})", best.skill.getName(), best.score);
        return null;
    }

    /**
     * 获取候选技能列表（供 UI 展示 Top-K）
     */
    public List<SkillRouteDto> getCandidates(String userInput, int maxCandidates) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }
        List<Bm25Score> scores = bm25ScoreAll(userInput.toLowerCase().trim());
        List<SkillRouteDto> result = new ArrayList<>();
        for (Bm25Score s : scores) {
            if (result.size() >= maxCandidates) break;
            double conf = normalizeBm25Score(s.score());
            if (conf >= CANDIDATE_THRESHOLD) {
                result.add(SkillRouteDto.semanticMatch(s.skill(), "bm25:candidate:" + s.skill().getName(), conf));
            }
        }
        return result;
    }

    /**
     * 对所有技能计算 BM25 分数，按分数降序排列。
     */
    private List<Bm25Score> bm25ScoreAll(String queryText) {
        if (searcherManager == null) {
            return List.of();
        }

        IndexSearcher searcher = null;
        try {
            searcher = searcherManager.acquire();
            Query query = buildQuery(queryText);
            TopDocs topDocs = searcher.search(query, skills.size());

            List<Bm25Score> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                String skillName = doc.get("skill_name");
                double rawScore = sd.score;
                SkillDefinition skill = findSkill(skillName);
                if (skill != null) {
                    results.add(new Bm25Score(skill, rawScore));
                }
            }

            results.sort(Comparator.comparingDouble((Bm25Score s) -> s.score()).reversed());
            return results;
        } catch (IOException e) {
            log.warn("BM25 搜索失败: {}", e.getMessage());
            return List.of();
        } finally {
            if (searcher != null) {
                try { searcherManager.release(searcher); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * 将用户输入解析为 Lucene 查询。
     * 使用 StandardAnalyzer 分词，每个 token 作为 SHOULD 条件（OR 语义）。
     */
    private Query buildQuery(String queryText) {
        try {
            org.apache.lucene.analysis.TokenStream tokens = analyzer.tokenStream("content", queryText);
            org.apache.lucene.analysis.tokenattributes.CharTermAttribute attr =
                    tokens.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
            tokens.reset();

            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            while (tokens.incrementToken()) {
                String term = attr.toString();
                if (term != null && !term.isBlank()) {
                    // wildcard 支持部分匹配，应对分词边界问题
                    Query tq = new WildcardQuery(new Term("content", "*" + term + "*"));
                    builder.add(tq, BooleanClause.Occur.SHOULD);
                }
            }
            tokens.close();

            BooleanQuery built = builder.build();
            // 如果没有任何 token，返回 match-all（避免空查询）
            if (built.clauses().isEmpty()) {
                return new org.apache.lucene.search.MatchAllDocsQuery();
            }
            return built;
        } catch (IOException e) {
            log.warn("BM25 查询构建失败，回退到 match-all: {}", e.getMessage());
            return new org.apache.lucene.search.MatchAllDocsQuery();
        }
    }

    /**
     * 将 BM25 原始分归一化到 [0, 1]。
     * 使用 sigmoid 归一化：1 - 1/(1+score)，score>=0 时单调递增，score->inf 时趋近 1.0。
     */
    private double normalizeBm25Score(double rawScore) {
        return 1.0 - 1.0 / (1.0 + rawScore);
    }

    /**
     * 构建 BM25 索引：每个 skill 一个 document，字段包括：
     * - skill_name: 技能名（StringField，不分析，用于检索后回填）
     * - skill_id: 元数据 name（StringField，存储用）
     * - content: description + triggerKeywords + fullContent前500字（TextField，分词后建立倒排索引）
     */
    private void buildIndex() {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (IndexWriter writer = new IndexWriter(indexDir, config)) {
            for (SkillDefinition skill : skills) {
                Document doc = new Document();
                doc.add(new StringField("skill_name", skill.getName(), Field.Store.YES));
                doc.add(new StringField("skill_id", skill.getMetadata().name(), Field.Store.YES));

                // content 字段：description + triggerKeywords 拼接
                StringBuilder content = new StringBuilder();
                content.append(skill.getMetadata().description()).append(' ');
                for (String kw : skill.getTriggerKeywords()) {
                    content.append(kw).append(' ');
                }
                // 同时把 fullContent 前 500 字也加入（提供更多语义上下文）
                String full = skill.getFullContent();
                if (full != null && !full.isBlank()) {
                    content.append(full.substring(0, Math.min(full.length(), 500))).append(' ');
                }
                doc.add(new TextField("content", content.toString().trim(), Field.Store.NO));

                writer.addDocument(doc);
            }
            writer.commit();
            // 刷新 searcher
            SearcherManager old = null;
            try {
                old = searcherManager;
            } catch (Exception ignored) { }
            searcherManager = new SearcherManager(indexDir, null);
            if (old != null) {
                try { old.close(); } catch (IOException ignored) { }
            }
            log.info("BM25 索引构建完成，共 {} 个 skill", skills.size());
        } catch (IOException e) {
            log.error("BM25 索引构建失败: {}", e.getMessage(), e);
        }
    }

    private SkillDefinition findSkill(String name) {
        for (SkillDefinition s : skills) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    /**
     * BM25 评分结果（不可变值对象）
     */
    private record Bm25Score(SkillDefinition skill, double score) {}
}
