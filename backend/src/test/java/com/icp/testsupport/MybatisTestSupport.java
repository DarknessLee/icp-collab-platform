package com.icp.testsupport;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.icp.system.entity.SysConfig;
import com.icp.system.entity.SysDept;
import com.icp.system.entity.SysDictData;
import com.icp.system.entity.SysDictType;
import com.icp.system.entity.SysFile;
import com.icp.system.entity.SysJob;
import com.icp.system.entity.SysLoginLog;
import com.icp.system.entity.SysMenu;
import com.icp.system.entity.SysOperLog;
import com.icp.system.entity.SysPost;
import com.icp.system.entity.SysRole;
import com.icp.system.entity.SysRoleMenu;
import com.icp.system.entity.SysUser;
import com.icp.system.entity.SysUserRole;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 单测用 MyBatis-Plus 元数据初始化。
 * <p>Lambda 条件构造器依赖 TableInfo 缓存，纯 Mockito 单测没有 Spring/MyBatis 启动流程，
 * 因此在测试前显式注册实体元数据，避免 "can not find lambda cache for this entity"。</p>
 */
public final class MybatisTestSupport {

    private static volatile boolean initialized;

    private MybatisTestSupport() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        List<Class<?>> entities = List.of(
                SysUser.class, SysDept.class, SysRole.class, SysMenu.class, SysUserRole.class,
                SysRoleMenu.class, SysOperLog.class, SysLoginLog.class, SysPost.class,
                SysDictType.class, SysDictData.class, SysConfig.class, SysJob.class, SysFile.class);
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
        initialized = true;
    }
}
