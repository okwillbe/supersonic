
# 项目包说明

`launchers/standalone` 是 SuperSonic 的**单机全量启动模块**。它将 Chat BI（问答）、Headless BI（语义层）、Auth（认证）等所有核心组件打包在一个可执行的 Spring Boot 应用中。这是最简单、最常用的部署方式，适合开发、测试以及中小型生产环境。


# 配置文件

application.yaml - Spring Boot主配置文件，定义了服务器端口、数据库类型选择、MyBatis映射器位置、日志级别、Swagger文档配置等核心应用设置

s2-config.yaml - SuperSonic业务配置文件，包含解析策略、缓存开关、演示数据、认证配置等业务相关设置

# 类说明

* **`com.tencent.supersonic.StandaloneLauncher`**
  * **作用**：整个应用程序的**主入口**（Main Class）。
  * **核心逻辑**：使用 standard `@SpringBootApplication` 注解启动 Spring 上下文，并扫描 `com.tencent.supersonic` 和 `dev.langchain4j` 包下的所有组件。它是构建可执行 JAR 包时指定的 `Main-Class`。

* **`com.tencent.supersonic.demo.S2VisitsDemo`** (及 `S2BaseDemo`)
  * **作用**：内置的**演示数据初始化器**。
  * **触发机制**：实现了 Spring 的 `CommandLineRunner` 接口，在应用启动后自动运行。
  * **功能**：会自动创建一个名为“超音数分析助手”的 Agent，初始化虚拟的访问日志数据（PV/UV、停留时长等），并生成相关的语义模型、指标和维度。这使得用户在初次部署后无需配置即可直接体验系统的问答能力（如提问：“访问过超音数的部门有哪些”）。

# Maven Assembly Plugin 使用说明

`maven-assembly-plugin` 是一个用于创建项目分发包（Distribution）的强大插件。在 SuperSonic 项目中，我们使用它将编译后的 JAR 包、依赖库、配置文件和启动脚本打包成一个可直接部署的 `.tar.gz` 压缩包。

## 1. 插件配置 (`pom.xml`)

在 `launchers/standalone/pom.xml` 中，插件配置如下：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.7.1</version>
    <configuration>
        <!-- 使用 GNU tar 扩展头，支持长文件名 -->
        <tarLongFileMode>gnu</tarLongFileMode>
        <!-- 设置 Manifest 中的 Main-Class，使 jar 可运行 -->
        <archive>
            <manifest>
                <mainClass>${start-class}</mainClass>
            </manifest>
        </archive>
        <!-- 指定外部的 Assembly 描述符文件 -->
        <descriptors>
            <descriptor>../../assembly/build/build.xml</descriptor>
        </descriptors>
    </configuration>
    <executions>
        <execution>
            <id>make-assembly</id>
            <!-- 绑定到 package 生命周期阶段 -->
            <phase>package</phase>
            <goals>
                <goal>single</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## 2. 打包描述符 (`assembly/build/build.xml`)

描述符文件定义了最终压缩包的目录结构。核心配置项说明：

* **id**: `bin` - 这会作为生成文件的后缀（例如 `*-bin.tar.gz`）。
* **formats**: `tar.gz` - 指定打包格式。
* **fileSets**: 定义文件复制规则：
    1. **Release 配置**: 将 `src/main/resources` 复制到包内的 `conf` 目录。
    2. **依赖库**: 将构建生成的 JAR 包及所有依赖复制到包内的 `lib` 目录。
    3. **脚本**: 将 `assembly/bin` 目录下的启动脚本（如 `supersonic-daemon.sh`）复制到包内的 `bin` 目录，并赋予**可执行权限** (`0755`)。

## 3. 如何构建

在项目根目录下，执行标准的 Maven 打包命令即可触发 Assembly 插件工作：

```bash
mvn clean package -DskipTests
```

## 4. 构建产物

构建成功后，在 `launchers/standalone/target/` 目录下会生成如下文件：

* **`launchers-standalone-{version}.jar`**: 仅包含项目代码的可执行 JAR。
* **`launchers-standalone-{version}-bin.tar.gz`**: 完整的发行包（包含 lib, conf, bin）。

### 解压后的目录结构

```text
supersonic/
├── bin/          # 启动脚本 (supersonic-daemon.sh, .bat)
├── conf/         # 配置文件 (application.yaml 等)
└── lib/          # 所有依赖 jar 包
```
