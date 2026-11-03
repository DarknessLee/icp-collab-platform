package com.icp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.icp.common.cache.CacheService;
import com.icp.system.entity.SysConfig;
import com.icp.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 系统参数读取：带 60s 缓存；写操作后失效缓存。
 * 密码策略、上传限制等运行期开关优先读 sys_config，缺省回退到代码常量。
 */
@Service
public class ConfigService {

    private static final String CACHE_PREFIX = "sys:config:";
    private static final long CACHE_TTL_SECONDS = 60L;

    private final SysConfigMapper configMapper;
    private final CacheService cacheService;
    private final ChangeLogService changeLogService;

    public ConfigService(SysConfigMapper configMapper, CacheService cacheService,
                         ChangeLogService changeLogService) {
        this.configMapper = configMapper;
        this.cacheService = cacheService;
        this.changeLogService = changeLogService;
    }

    public List<SysConfig> list() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId));
    }

    public com.icp.common.api.PageResult<SysConfig> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SysConfig::getConfigName, keyword.trim())
                    .or().like(SysConfig::getConfigKey, keyword.trim()));
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SysConfig> page =
                configMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        pageNum, pageSize), wrapper);
        return new com.icp.common.api.PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public Optional<String> get(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        String cacheKey = CACHE_PREFIX + key;
        Object cached = cacheService.get(cacheKey).orElse(null);
        if (cached instanceof String value) {
            return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
        }
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        String value = config == null ? null : config.getConfigValue();
        cacheService.put(cacheKey, value == null ? "" : value, CACHE_TTL_SECONDS);
        return StringUtils.hasText(value) ? Optional.of(value) : Optional.empty();
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(value -> {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(value -> "true".equalsIgnoreCase(value.trim())).orElse(defaultValue);
    }

    public void evict(String key) {
        cacheService.evict(CACHE_PREFIX + key);
    }

    public SysConfig requireById(Long id) {
        SysConfig config = configMapper.selectById(id);
        if (config == null) {
            throw new com.icp.common.exception.BusinessException(com.icp.common.api.ErrorCode.NOT_FOUND, "参数不存在");
        }
        return config;
    }

    public void updateValue(Long id, String value) {
        SysConfig config = requireById(id);
        if ("Y".equals(config.getConfigType())) {
            throw new com.icp.common.exception.BusinessException(
                    com.icp.common.api.ErrorCode.PARAM_ERROR, "系统内置参数不允许修改");
        }
        String beforeValue = config.getConfigValue();
        config.setConfigValue(value);
        configMapper.updateById(config);
        evict(config.getConfigKey());
        java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
        before.put("configKey", config.getConfigKey());
        before.put("configValue", beforeValue);
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("configKey", config.getConfigKey());
        after.put("configValue", value);
        changeLogService.record("sys_config", id, before, after);
    }
}
