package com.icp.workflow.service;

import com.icp.workflow.entity.WfProcessMeta;
import com.icp.workflow.mapper.WfProcessMetaMapper;
import com.icp.workflow.support.WorkflowConstants;
import jakarta.annotation.PostConstruct;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义服务：启动时把 6 条 BPMN 以固定部署名（icp-processes）部署到 Flowable，
 * 并同步 wf_process_meta.version 为引擎中的最新版本（docs/03 §5.1 第 2 条）。
 */
@Service
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final ProcessEngine processEngine;
    private final RepositoryService repositoryService;
    private final WfProcessMetaMapper processMetaMapper;

    public WorkflowDefinitionService(ProcessEngine processEngine,
                                     RepositoryService repositoryService,
                                     WfProcessMetaMapper processMetaMapper) {
        this.processEngine = processEngine;
        this.repositoryService = repositoryService;
        this.processMetaMapper = processMetaMapper;
    }

    @PostConstruct
    public void init() {
        for (String key : WorkflowConstants.PROCESS_KEYS) {
            try {
                deployIfChanged(key);
            } catch (Exception e) {
                log.error("流程部署失败: {}", key, e);
            }
            try {
                syncMetaVersion(key);
            } catch (Exception e) {
                log.warn("同步流程版本失败: {}", key, e);
            }
        }
    }

    private void deployIfChanged(String key) throws IOException {
        String resourceName = key + ".bpmn20.xml";
        ClassPathResource resource = new ClassPathResource("processes/" + resourceName);
        if (!resource.exists()) {
            log.warn("BPMN 资源不存在: {}", resourceName);
            return;
        }
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = readAll(in);
        }
        ProcessDefinition latest = latestDefinition(key);
        if (latest != null) {
            try (InputStream in = repositoryService.getResourceAsStream(latest.getDeploymentId(), latest.getResourceName())) {
                byte[] deployed = readAll(in);
                if (java.util.Arrays.equals(deployed, bytes)) {
                    return;
                }
            } catch (Exception ignored) {
                // 资源读取异常时按需要重新部署处理
            }
        }
        Deployment deployment = repositoryService.createDeployment()
                .name(WorkflowConstants.DEPLOYMENT_NAME)
                .addInputStream(resourceName, new java.io.ByteArrayInputStream(bytes))
                .deploy();
        log.info("流程已部署: key={} deploymentId={} version={}", key, deployment.getId(), latestDefinition(key) == null ? 1 : latestDefinition(key).getVersion());
    }

    private void syncMetaVersion(String key) {
        ProcessDefinition latest = latestDefinition(key);
        if (latest == null) {
            return;
        }
        WfProcessMeta meta = processMetaMapper.selectByProcessKey(key);
        if (meta != null && !Integer.valueOf(latest.getVersion()).equals(meta.getVersion())) {
            meta.setVersion(latest.getVersion());
            processMetaMapper.updateById(meta);
            log.info("同步流程元数据版本: {} -> {}", key, latest.getVersion());
        }
    }

    public ProcessDefinition latestDefinition(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
    }

    /** 流程定义列表（wf_process_meta + 引擎最新版本）。 */
    public List<Map<String, Object>> listProcesses() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String key : WorkflowConstants.PROCESS_KEYS) {
            WfProcessMeta meta = processMetaMapper.selectByProcessKey(key);
            ProcessDefinition definition = latestDefinition(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("processKey", key);
            item.put("processName", meta != null ? meta.getProcessName() : WorkflowConstants.PROCESS_NAMES.get(key));
            item.put("category", meta != null ? meta.getCategory() : null);
            item.put("description", meta != null ? meta.getDescription() : null);
            item.put("version", definition != null ? definition.getVersion() : (meta != null ? meta.getVersion() : null));
            item.put("metaVersion", meta != null ? meta.getVersion() : null);
            item.put("status", definition != null ? 1 : 0);
            item.put("deploymentId", definition != null ? definition.getDeploymentId() : null);
            item.put("formCode", key);
            list.add(item);
        }
        return list;
    }

    /** 流程 BPMN XML（前端 bpmn-js 只读渲染）。 */
    public Map<String, Object> diagram(String processKey) {
        ProcessDefinition definition = latestDefinition(processKey);
        if (definition == null) {
            throw new com.icp.common.exception.BusinessException(com.icp.common.api.ErrorCode.NOT_FOUND, "流程定义不存在");
        }
        String xml;
        try (InputStream in = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
            xml = new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new com.icp.common.exception.BusinessException(com.icp.common.api.ErrorCode.SYSTEM_ERROR, "流程定义读取失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processKey", processKey);
        data.put("processName", definition.getName());
        data.put("version", definition.getVersion());
        data.put("xml", xml);
        data.put("bpmnXml", xml);
        return data;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
