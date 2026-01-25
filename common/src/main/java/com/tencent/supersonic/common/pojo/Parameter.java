package com.tencent.supersonic.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 参数配置类
 *
 * <p>
 * 用于表示系统中的各种配置参数，支持多种数据类型和前端展示形式。
 * 该类是系统配置管理的核心数据模型，可以表示从简单的文本输入到复杂的下拉选择等多种参数类型。
 *
 * <p>
 * 支持的参数类型包括：
 * <ul>
 * <li>1. 密码字段 (Password Field): dataType: "string", name: "password"</li>
 * <li>2. 文本输入字段 (Text Input Field): dataType: "string"</li>
 * <li>3. 长文本输入字段 (Long Text Input Field): dataType: "longText"</li>
 * <li>4. 数字输入字段 (Number Input Field): dataType: "number"</li>
 * <li>5. 开关组件 (Switch Component): dataType: "bool"</li>
 * <li>6. 下拉选择组件 (Select Dropdown Component): dataType: "list", candidateValues:
 * ["OPEN_AI", "OLLAMA"]</li>
 * </ul>
 *
 * <p>
 * 每个参数可以指定：
 * <ul>
 * <li>require: 是否必填（true/false）</li>
 * <li>placeholder: 占位符提示信息</li>
 * <li>value: 初始值或当前值</li>
 * <li>defaultValue: 默认值（当 value 为空时使用）</li>
 * <li>candidateValues: 候选值列表（用于下拉选择等场景）</li>
 * <li>dependencies: 参数间的依赖关系配置</li>
 * </ul>
 *
 * @author SuperSonic Team
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Parameter {

    /** 参数名称，用于唯一标识该参数 */
    private String name;

    /** 默认值，当用户未设置值时使用此默认值 */
    private String defaultValue;

    /** 参数备注，用于在前端展示简短的说明信息 */
    private String comment;

    /** 参数描述，用于详细说明该参数的用途和配置方式 */
    private String description;

    /** 数据类型，用于前端渲染不同的输入组件。支持：string, longText, number, bool, list */
    private String dataType;

    /** 所属模块，用于对参数进行分类管理 */
    private String module;

    /** 当前值，用户通过配置界面设置的值 */
    private String value;

    /** 候选值列表，用于下拉选择等需要预定义选项的场景 */
    private List<String> candidateValues;

    /** 依赖关系列表，定义该参数与其他参数之间的显示/隐藏或默认值联动关系 */
    private List<Dependency> dependencies;

    /**
     * 构造一个不带候选值和依赖关系的参数对象
     *
     * @param name         参数名称
     * @param defaultValue 默认值
     * @param comment      参数备注
     * @param description  参数详细描述
     * @param dataType     数据类型
     * @param module       所属模块
     */
    public Parameter(String name, String defaultValue, String comment, String description,
            String dataType, String module) {
        this(name, defaultValue, comment, description, dataType, module, null, null);
    }

    /**
     * 构造一个带候选值但不带依赖关系的参数对象
     *
     * @param name            参数名称
     * @param defaultValue    默认值
     * @param comment         参数备注
     * @param description     参数详细描述
     * @param dataType        数据类型
     * @param module          所属模块
     * @param candidateValues 候选值列表，用于下拉选择等场景
     */
    public Parameter(String name, String defaultValue, String comment, String description,
            String dataType, String module, List<String> candidateValues) {
        this(name, defaultValue, comment, description, dataType, module, candidateValues, null);
    }

    /**
     * 构造一个完整的参数对象（全参构造器）
     *
     * <p>
     * 该构造器支持所有字段的初始化，包括候选值列表和依赖关系配置。
     * 通过依赖关系配置，可以实现参数间的联动效果，例如：
     * <ul>
     * <li>当参数 A 的值为 X 时，显示参数 B</li>
     * <li>当参数 A 的值为 Y 时，自动设置参数 C 的默认值为 Z</li>
     * </ul>
     *
     * @param name            参数名称
     * @param defaultValue    默认值
     * @param comment         参数备注
     * @param description     参数详细描述
     * @param dataType        数据类型（string, longText, number, bool, list）
     * @param module          所属模块
     * @param candidateValues 候选值列表，为 null 时表示该参数不需要候选值
     * @param dependencies    依赖关系列表，为 null 时表示该参数无依赖关系
     */
    public Parameter(String name, String defaultValue, String comment, String description,
            String dataType, String module, List<String> candidateValues,
            List<Dependency> dependencies) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.comment = comment;
        this.description = description;
        this.dataType = dataType;
        this.module = module;
        this.candidateValues = candidateValues;
        this.dependencies = dependencies;
    }

    /**
     * 获取参数的有效值
     *
     * <p>
     * 采用智能回退机制：
     * <ul>
     * <li>如果用户已设置当前值（value 不为 null 且非空），则返回当前值</li>
     * <li>如果用户未设置或值为空字符串，则返回默认值（defaultValue）</li>
     * </ul>
     *
     * <p>
     * 这种设计确保了参数始终能返回一个有效值，避免了配置缺失导致的系统异常。
     *
     * @return 参数的有效值，优先返回用户设置的值，否则返回默认值
     */
    public String getValue() {
        // 当用户未设置值或值为空字符串时，使用默认值
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 设置参数的当前值
     *
     * <p>
     * 该方法由用户通过配置界面或 API 调用，用于更新参数的实际值。
     *
     * @param value 要设置的新值
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * 参数依赖关系配置类
     *
     * <p>
     * 用于定义参数之间的联动关系，支持以下两种依赖类型：
     * <ul>
     * <li>显示/隐藏依赖：根据依赖参数的值决定当前参数是否显示（show 配置）</li>
     * <li>默认值依赖：根据依赖参数的值自动设置当前参数的默认值（setDefaultValue 配置）</li>
     * </ul>
     *
     * <p>
     * 示例场景：
     * 
     * <pre>
     * // 当 "model_type" 参数的值为 "OPEN_AI" 时，显示 "api_key" 参数
     * Dependency dependency = new Dependency();
     * dependency.setName("model_type");
     * dependency.setShow(new Show(Arrays.asList("OPEN_AI")));
     * </pre>
     */
    @Data
    public static class Dependency {
        /** 依赖的参数名称 */
        private String name;

        /** 显示条件配置，定义何时显示当前参数 */
        private Show show;

        /** 默认值映射表，根据依赖参数的不同值设置当前参数的默认值 */
        private Map<String, String> setDefaultValue;

        /**
         * 显示条件配置类
         *
         * <p>
         * 用于定义参数的显示条件，只有当依赖参数的值包含在 includesValue 列表中时，
         * 当前参数才会在前端界面显示。
         *
         * <p>
         * 这种机制可以简化配置界面，只在必要时显示相关参数，提升用户体验。
         */
        @Data
        public static class Show {
            /**
             * 触发显示的值列表
             * 当依赖参数的值在此列表中时，当前参数将被显示
             */
            private List<String> includesValue;
        }
    }
}
