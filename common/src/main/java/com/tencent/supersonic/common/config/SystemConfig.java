package com.tencent.supersonic.common.config;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.Parameter;
import com.tencent.supersonic.common.util.ContextUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置类
 *
 * <p>
 * 该类是系统级配置管理的核心数据模型，负责管理系统管理员列表和各种系统参数配置。
 * 该类采用了"默认值合并策略"，即系统会先构建所有的默认参数，然后用用户自定义的值覆盖默认值。
 *
 * <p>
 * 主要功能包括：
 * <ul>
 * <li>管理系统管理员列表（支持多个管理员）</li>
 * <li>管理系统级参数配置（如 LLM 配置、数据库配置等）</li>
 * <li>从 Spring 容器中动态收集所有 ParameterConfig Bean 的默认参数</li>
 * <li>将用户自定义的参数值与默认参数进行智能合并</li>
 * </ul>
 *
 * <p>
 * 设计要点：
 * <ul>
 * <li>通过 {@link #buildDefaultParameters()} 方法自动发现所有模块的默认参数配置</li>
 * <li>通过 {@link #getParameters()} 方法实现参数值的智能合并（用户值优先于默认值）</li>
 * <li>管理员列表以逗号分隔的字符串形式存储，便于序列化和反序列化</li>
 * </ul>
 *
 * @author SuperSonic Team
 */
@Data
public class SystemConfig {

    /** 系统配置的唯一标识 ID */
    private Integer id;

    /** 系统管理员列表，存储所有具有管理权限的用户名 */
    private List<String> admins;

    /** 用户自定义的参数列表，用于覆盖系统默认参数 */
    private List<Parameter> parameters;

    /**
     * 初始化系统配置
     *
     * <p>
     * 该方法用于初始化系统配置的默认值，通常在系统首次安装或重置配置时调用。
     * 初始化操作包括：
     * <ul>
     * <li>从所有模块收集默认参数配置</li>
     * <li>设置默认管理员为 "admin"</li>
     * </ul>
     */
    public void init() {
        parameters = buildDefaultParameters();
        admins = Lists.newArrayList("admin");
    }

    /**
     * 获取管理员列表的字符串表示
     *
     * <p>
     * 将管理员列表转换为逗号分隔的字符串，便于存储和传输。
     * 该方法主要用于将管理员列表持久化到数据库或配置文件中。
     *
     * @return 逗号分隔的管理员列表字符串，如 "admin,user1,user2"；如果没有管理员则返回空字符串
     */
    public String getAdmin() {
        if (CollectionUtils.isEmpty(admins)) {
            return "";
        }
        return StringUtils.join(admins, ",");
    }

    /**
     * 根据参数名称获取参数值
     *
     * <p>
     * 该方法通过参数名称查找对应的参数值，查找过程包括：
     * <ul>
     * <li>构建默认参数列表</li>
     * <li>应用用户自定义的参数值</li>
     * <li>返回最终的参数值</li>
     * </ul>
     *
     * <p>
     * 如果存在重复的参数名称，会使用第一个找到的参数值（通过 toMap 的合并函数 (k1, k2) -> k1）。
     *
     * @param name 参数名称，不能为空
     * @return 参数的有效值；如果参数名为空或参数不存在则返回空字符串
     */
    public String getParameterByName(String name) {
        // 参数名称为空时直接返回空字符串
        if (StringUtils.isBlank(name)) {
            return "";
        }
        // 构建参数名到参数值的映射表，遇到重复的 key 时保留第一个值
        Map<String, String> nameToValue = getParameters().stream()
                .collect(Collectors.toMap(Parameter::getName, Parameter::getValue, (k1, k2) -> k1));
        return nameToValue.get(name);
    }

    /**
     * 设置管理员列表
     *
     * <p>
     * 将以逗号分隔的管理员字符串解析为管理员列表。
     * 该方法主要用于从数据库或配置文件中加载管理员列表。
     *
     * @param admin 逗号分隔的管理员列表字符串，如 "admin,user1,user2"；如果为空则设置为空列表
     */
    public void setAdminList(String admin) {
        if (StringUtils.isNotBlank(admin)) {
            // 将逗号分隔的字符串拆分为管理员列表
            admins = Arrays.asList(admin.split(","));
        } else {
            admins = Lists.newArrayList();
        }
    }

    /**
     * 构建默认参数列表
     *
     * <p>
     * 该方法通过 Spring 容器的自动发现机制，收集所有实现了 {@link ParameterConfig} 接口的 Bean，
     * 并汇总它们提供的默认参数配置。这种设计使得各个模块可以独立声明自己的参数配置，
     * 而无需在此类中硬编码所有参数。
     *
     * <p>
     * 设计优势：
     * <ul>
     * <li>模块化：每个模块通过实现 ParameterConfig 接口声明自己的参数</li>
     * <li>可扩展：新增模块时无需修改此类，只需实现 ParameterConfig 接口</li>
     * <li>集中管理：所有模块的参数在此处统一汇总</li>
     * </ul>
     *
     * @return 汇总后的默认参数列表，包含所有模块的默认参数
     */
    private List<Parameter> buildDefaultParameters() {
        List<Parameter> defaultParameters = Lists.newArrayList();
        // 从 Spring 容器中获取所有 ParameterConfig 类型的 Bean
        Collection<ParameterConfig> configurableParameters = ContextUtils.getBeansOfType(ParameterConfig.class)
                .values();
        // 遍历所有 ParameterConfig Bean，汇总它们的默认参数
        for (ParameterConfig configParameters : configurableParameters) {
            defaultParameters.addAll(configParameters.getSysParameters());
        }
        return defaultParameters;
    }

    /**
     * 获取最终生效的参数列表
     *
     * <p>
     * 该方法是参数管理的核心逻辑，实现了"用户值优先，默认值兜底"的合并策略：
     * <ol>
     * <li>首先构建所有模块的默认参数列表</li>
     * <li>如果用户未自定义任何参数，直接返回默认参数列表</li>
     * <li>如果用户有自定义参数，将用户值映射到默认参数上（用户值覆盖默认值）</li>
     * <li>对于用户未设置的参数，保留默认值</li>
     * </ol>
     *
     * <p>
     * 合并策略说明：
     * <ul>
     * <li>通过 toMap 的合并函数 (v1, v2) -> v2，当用户多次设置同一参数时，使用最后一次的值</li>
     * <li>通过 getOrDefault，确保每个参数都有值（用户值或默认值）</li>
     * </ul>
     *
     * @return 最终生效的参数列表，每个参数都包含有效值（用户自定义值或默认值）
     */
    public List<Parameter> getParameters() {
        // 构建所有模块的默认参数列表
        List<Parameter> defaultParameters = buildDefaultParameters();

        // 如果用户未自定义任何参数，直接返回默认参数
        if (CollectionUtils.isEmpty(parameters)) {
            return defaultParameters;
        }

        // 将用户自定义的参数转换为 Map，便于快速查找（key 冲突时保留后者）
        Map<String, String> parameterNameValueMap = parameters.stream()
                .collect(Collectors.toMap(Parameter::getName, Parameter::getValue, (v1, v2) -> v2));

        // 将用户自定义的值应用到默认参数上
        for (Parameter parameter : defaultParameters) {
            // 优先使用用户自定义的值，如果用户未设置则使用默认值
            parameter.setValue(parameterNameValueMap.getOrDefault(parameter.getName(),
                    parameter.getDefaultValue()));
        }

        return defaultParameters;
    }
}
