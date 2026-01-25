package com.tencent.supersonic.common.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.tencent.supersonic.common.config.SystemConfig;
import com.tencent.supersonic.common.persistence.dataobject.SystemConfigDO;
import com.tencent.supersonic.common.persistence.mapper.SystemConfigMapper;
import com.tencent.supersonic.common.pojo.Parameter;
import com.tencent.supersonic.common.service.SystemConfigService;
import com.tencent.supersonic.common.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfigDO>
        implements SystemConfigService {

    @Autowired
    private Environment environment;

    // AtomicReference用于实现原子性的对象引用操作。它可以在多线程环境下安全地更新对象引用，而无需使用传统的 synchronized关键字
    private AtomicReference<SystemConfig> cachedSystemConfig = new AtomicReference<>();

    @Override
    public SystemConfig getSystemConfig() {
        SystemConfig cachedConfig = cachedSystemConfig.get();
        if (cachedConfig != null) {
            return cachedConfig;
        }
        SystemConfig systemConfigDb = getSystemConfigFromDB();
        cachedSystemConfig.set(systemConfigDb);
        return systemConfigDb;
    }

    private SystemConfig getSystemConfigFromDB() { // 加上id ，如果有多条记录，会出错
        List<SystemConfigDO> list = this.lambdaQuery().eq(SystemConfigDO::getId, 1).list();
        if (CollectionUtils.isEmpty(list)) {
            SystemConfig systemConfig = new SystemConfig();
            systemConfig.setId(1);
            systemConfig.init();
            // use system property to initialize system parameter
            systemConfig.getParameters().stream().forEach(p -> {
                if (environment.containsProperty(p.getName())) {
                    p.setValue(environment.getProperty(p.getName()));
                }
            });
            save(systemConfig);
            return systemConfig;
        }

        return convert(list.iterator().next());
    }

    @Override
    public void save(SystemConfig sysConfig) {
        SystemConfigDO systemConfigDO = convert(sysConfig);
        saveOrUpdate(systemConfigDO);
        cachedSystemConfig.set(sysConfig);
    }

    private SystemConfig convert(SystemConfigDO systemConfigDO) {
        SystemConfig sysParameter = new SystemConfig();
        sysParameter.setId(systemConfigDO.getId());

        // 使用 TypeReference 解决 Java 泛型擦除问题。
        // 在运行时，List.class 会丢失 <Parameter> 类型信息，导致 Jackson 默认将其反序列化为
        // List<LinkedHashMap>。
        // 通过匿名内部类 TypeReference<List<Parameter>> 可以保留完整的泛型类型信息，确保正确转换为 List<Parameter>。
        List<Parameter> parameters = JsonUtil.toObject(systemConfigDO.getParameters(),
                new TypeReference<List<Parameter>>() {
                });

        sysParameter.setParameters(parameters);
        sysParameter.setAdminList(systemConfigDO.getAdmin());
        return sysParameter;
    }

    private SystemConfigDO convert(SystemConfig sysParameter) {
        SystemConfigDO sysParameterDO = new SystemConfigDO();
        sysParameterDO.setId(sysParameter.getId());
        sysParameterDO.setParameters(JSONObject.toJSONString(sysParameter.getParameters()));
        sysParameterDO.setAdmin(sysParameter.getAdmin());
        return sysParameterDO;
    }
}
