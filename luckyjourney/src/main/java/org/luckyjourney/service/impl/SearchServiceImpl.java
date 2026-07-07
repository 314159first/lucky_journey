package org.luckyjourney.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.luckyjourney.config.ElasticsearchSearchProperties;
import org.luckyjourney.constant.AuditStatus;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.entity.video.Type;
import org.luckyjourney.entity.video.Video;
import org.luckyjourney.entity.vo.BasePage;
import org.luckyjourney.entity.vo.UserVO;
import org.luckyjourney.mapper.user.UserMapper;
import org.luckyjourney.mapper.video.TypeMapper;
import org.luckyjourney.mapper.video.VideoMapper;
import org.luckyjourney.service.SearchService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired(required = false)
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ElasticsearchSearchProperties properties;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TypeMapper typeMapper;

    private volatile boolean indicesReady = false;

    @Override
    public IPage<Video> searchVideos(String keyword, BasePage basePage) {
        if (!isEnabled()) {
            return null;
        }
        try {
            ensureIndices();
            SearchRequest request = new SearchRequest(properties.getVideoIndex());
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.from(offset(basePage));
            source.size(limit(basePage));
            source.timeout(TimeValue.timeValueSeconds(3));
            source.query(buildVideoQuery(keyword));
            source.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
            source.sort("gmtCreated", SortOrder.DESC);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            if (response.status() != RestStatus.OK) {
                return null;
            }
            return toVideoPage(response, basePage);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public IPage<UserVO> searchUsers(String keyword, BasePage basePage) {
        if (!isEnabled()) {
            return searchUsersByMysql(keyword, basePage);
        }
        try {
            ensureIndices();
            SearchRequest request = new SearchRequest(properties.getUserIndex());
            SearchSourceBuilder source = new SearchSourceBuilder();
            source.from(offset(basePage));
            source.size(limit(basePage));
            source.timeout(TimeValue.timeValueSeconds(3));
            source.query(buildUserQuery(keyword));
            source.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
            source.sort("gmtCreated", SortOrder.DESC);
            request.source(source);

            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            if (response.status() != RestStatus.OK) {
                return searchUsersByMysql(keyword, basePage);
            }
            IPage<UserVO> page = toUserPage(response, basePage);
            return page.getTotal() == 0 ? searchUsersByMysql(keyword, basePage) : page;
        } catch (Exception e) {
            return searchUsersByMysql(keyword, basePage);
        }
    }

    @Override
    public void syncVideo(Video video) {
        if (!isEnabled() || video == null || video.getId() == null) {
            return;
        }
        try {
            ensureIndices();
            if (!canSearch(video)) {
                deleteVideo(video.getId());
                return;
            }
            restHighLevelClient.index(new IndexRequest(properties.getVideoIndex())
                    .id(video.getId().toString())
                    .source(toVideoSource(video)), RequestOptions.DEFAULT);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void syncVideoById(Long videoId) {
        if (videoId == null) {
            return;
        }
        syncVideo(videoMapper.selectById(videoId));
    }

    @Override
    public void deleteVideo(Long videoId) {
        if (!isEnabled() || videoId == null) {
            return;
        }
        try {
            ensureIndices();
            restHighLevelClient.delete(new DeleteRequest(properties.getVideoIndex(), videoId.toString()), RequestOptions.DEFAULT);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void syncUser(User user) {
        if (!isEnabled() || user == null || user.getId() == null) {
            return;
        }
        try {
            ensureIndices();
            restHighLevelClient.index(new IndexRequest(properties.getUserIndex())
                    .id(user.getId().toString())
                    .source(toUserSource(user)), RequestOptions.DEFAULT);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void rebuildAll() {
        if (!isEnabled()) {
            return;
        }
        try {
            ensureIndices();
            BulkRequest videoBulk = new BulkRequest();
            List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                    .eq(Video::getAuditStatus, AuditStatus.SUCCESS)
                    .eq(Video::getOpen, false));
            for (Video video : videos) {
                videoBulk.add(new IndexRequest(properties.getVideoIndex())
                        .id(video.getId().toString())
                        .source(toVideoSource(video)));
            }
            if (videoBulk.numberOfActions() > 0) {
                restHighLevelClient.bulk(videoBulk, RequestOptions.DEFAULT);
            }

            BulkRequest userBulk = new BulkRequest();
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .select(User::getId, User::getNickName, User::getDescription, User::getAvatar, User::getSex, User::getGmtCreated));
            for (User user : users) {
                userBulk.add(new IndexRequest(properties.getUserIndex())
                        .id(user.getId().toString())
                        .source(toUserSource(user)));
            }
            if (userBulk.numberOfActions() > 0) {
                restHighLevelClient.bulk(userBulk, RequestOptions.DEFAULT);
            }
        } catch (Exception ignored) {
        }
    }

    private FunctionScoreQueryBuilder buildVideoQuery(String keyword) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("auditStatus", AuditStatus.SUCCESS))
                .filter(QueryBuilders.termQuery("open", false));

        if (!StringUtils.hasText(keyword)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else if (keyword.contains("YV")) {
            boolQuery.must(QueryBuilders.termQuery("yv", keyword));
        } else {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword,
                    "title^5",
                    "labelNames^4",
                    "description^2",
                    "typeName^2",
                    "userNickName"));
        }

        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("startCount").factor(0.25f).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("favoritesCount").factor(0.35f).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("historyCount").factor(0.08f).missing(0)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        ScoreFunctionBuilders.fieldValueFactorFunction("shareCount").factor(0.2f).missing(0))
        };

        return QueryBuilders.functionScoreQuery(boolQuery, functions)
                .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                .boostMode(CombineFunction.SUM);
    }

    private BoolQueryBuilder buildUserQuery(String keyword) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (!StringUtils.hasText(keyword)) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        } else {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "nickName^4", "description"));
        }
        return boolQuery;
    }

    private IPage<Video> toVideoPage(SearchResponse response, BasePage basePage) {
        Page<Video> page = new Page<>(pageNo(basePage), limit(basePage));
        page.setTotal(response.getHits().getTotalHits() == null ? 0 : response.getHits().getTotalHits().value);
        List<Long> ids = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            ids.add(Long.valueOf(hit.getId()));
        }
        if (ids.isEmpty()) {
            page.setRecords(Collections.emptyList());
            return page;
        }
        List<Video> videos = videoMapper.selectBatchIds(ids);
        Map<Long, Video> videoMap = videos.stream().collect(Collectors.toMap(Video::getId, video -> video));
        List<Video> ordered = ids.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        page.setRecords(ordered);
        return page;
    }

    private IPage<UserVO> toUserPage(SearchResponse response, BasePage basePage) {
        Page<UserVO> page = new Page<>(pageNo(basePage), limit(basePage));
        page.setTotal(response.getHits().getTotalHits() == null ? 0 : response.getHits().getTotalHits().value);
        List<UserVO> users = new ArrayList<>();
        for (SearchHit hit : response.getHits().getHits()) {
            Map<String, Object> source = hit.getSourceAsMap();
            UserVO userVO = new UserVO();
            userVO.setId(toLong(source.get("id")));
            userVO.setNickName(toString(source.get("nickName")));
            userVO.setDescription(toString(source.get("description")));
            userVO.setAvatar(toLong(source.get("avatar")));
            userVO.setSex((Boolean) source.get("sex"));
            users.add(userVO);
        }
        page.setRecords(users);
        return page;
    }

    private IPage<UserVO> searchUsersByMysql(String keyword, BasePage basePage) {
        Page<User> userPage = new Page<>(pageNo(basePage), limit(basePage));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getNickName, User::getDescription, User::getAvatar, User::getSex);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getNickName, keyword).or().like(User::getDescription, keyword));
        }
        IPage<User> dbPage = userMapper.selectPage(userPage, wrapper);
        Page<UserVO> result = new Page<>(pageNo(basePage), limit(basePage));
        result.setTotal(dbPage.getTotal());
        List<UserVO> users = dbPage.getRecords().stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
        result.setRecords(users);
        return result;
    }

    private Map<String, Object> toVideoSource(Video video) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", video.getId());
        source.put("yv", video.getYv());
        source.put("title", value(video.getTitle()));
        source.put("description", value(video.getDescription()));
        source.put("labelNames", value(video.getLabelNames()));
        source.put("typeId", video.getTypeId());
        source.put("typeName", getTypeName(video.getTypeId()));
        source.put("userId", video.getUserId());
        source.put("userNickName", getUserNickName(video.getUserId()));
        source.put("cover", video.getCover());
        source.put("url", video.getUrl());
        source.put("duration", video.getDuration());
        source.put("auditStatus", video.getAuditStatus());
        source.put("open", Boolean.TRUE.equals(video.getOpen()));
        source.put("startCount", number(video.getStartCount()));
        source.put("favoritesCount", number(video.getFavoritesCount()));
        source.put("historyCount", number(video.getHistoryCount()));
        source.put("shareCount", number(video.getShareCount()));
        source.put("gmtCreated", time(video.getGmtCreated()));
        return source;
    }

    private Map<String, Object> toUserSource(User user) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", user.getId());
        source.put("nickName", value(user.getNickName()));
        source.put("description", value(user.getDescription()));
        source.put("avatar", user.getAvatar());
        source.put("sex", user.getSex());
        source.put("gmtCreated", time(user.getGmtCreated()));
        return source;
    }

    private void ensureIndices() throws IOException {
        if (indicesReady) {
            return;
        }
        synchronized (this) {
            if (indicesReady) {
                return;
            }
            createIndexIfAbsent(properties.getVideoIndex(), videoMapping());
            createIndexIfAbsent(properties.getUserIndex(), userMapping());
            indicesReady = true;
        }
    }

    private void createIndexIfAbsent(String index, String mapping) throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(index);
        if (!restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT)) {
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
            createIndexRequest.source(mapping, XContentType.JSON);
            restHighLevelClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
        }
    }

    private String videoMapping() {
        return "{"
                + "\"mappings\":{\"properties\":{"
                + "\"id\":{\"type\":\"long\"},"
                + "\"yv\":{\"type\":\"keyword\"},"
                + "\"title\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
                + "\"description\":{\"type\":\"text\"},"
                + "\"labelNames\":{\"type\":\"text\"},"
                + "\"typeId\":{\"type\":\"long\"},"
                + "\"typeName\":{\"type\":\"text\"},"
                + "\"userId\":{\"type\":\"long\"},"
                + "\"userNickName\":{\"type\":\"text\"},"
                + "\"cover\":{\"type\":\"long\"},"
                + "\"url\":{\"type\":\"long\"},"
                + "\"duration\":{\"type\":\"keyword\"},"
                + "\"auditStatus\":{\"type\":\"integer\"},"
                + "\"open\":{\"type\":\"boolean\"},"
                + "\"startCount\":{\"type\":\"long\"},"
                + "\"favoritesCount\":{\"type\":\"long\"},"
                + "\"historyCount\":{\"type\":\"long\"},"
                + "\"shareCount\":{\"type\":\"long\"},"
                + "\"gmtCreated\":{\"type\":\"date\"}"
                + "}}}";
    }

    private String userMapping() {
        return "{"
                + "\"mappings\":{\"properties\":{"
                + "\"id\":{\"type\":\"long\"},"
                + "\"nickName\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
                + "\"description\":{\"type\":\"text\"},"
                + "\"avatar\":{\"type\":\"long\"},"
                + "\"sex\":{\"type\":\"boolean\"},"
                + "\"gmtCreated\":{\"type\":\"date\"}"
                + "}}}";
    }

    private boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled()) && restHighLevelClient != null;
    }

    private boolean canSearch(Video video) {
        return video != null
                && video.getId() != null
                && AuditStatus.SUCCESS.equals(video.getAuditStatus())
                && !Boolean.TRUE.equals(video.getOpen());
    }

    private String getUserNickName(Long userId) {
        if (userId == null) {
            return "";
        }
        User user = userMapper.selectById(userId);
        return user == null ? "" : value(user.getNickName());
    }

    private String getTypeName(Long typeId) {
        if (typeId == null) {
            return "";
        }
        Type type = typeMapper.selectById(typeId);
        return type == null ? "" : value(type.getName());
    }

    private int offset(BasePage basePage) {
        return (int) ((pageNo(basePage) - 1) * limit(basePage));
    }

    private long pageNo(BasePage basePage) {
        return basePage == null || basePage.getPage() == null ? 1L : basePage.getPage();
    }

    private int limit(BasePage basePage) {
        long limit = basePage == null || basePage.getLimit() == null ? 15L : basePage.getLimit();
        return (int) Math.max(1L, Math.min(limit, 100L));
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private Long number(Long value) {
        return value == null ? 0L : value;
    }

    private Long time(Date date) {
        return ObjectUtils.isEmpty(date) ? 0L : date.getTime();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }

    private String toString(Object value) {
        return value == null ? "" : value.toString();
    }
}
